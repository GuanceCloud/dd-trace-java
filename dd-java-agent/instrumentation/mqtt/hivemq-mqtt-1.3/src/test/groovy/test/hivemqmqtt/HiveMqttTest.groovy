package test.hivemqmqtt

import static datadog.trace.agent.test.utils.TraceUtils.basicSpan
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import static java.util.concurrent.TimeUnit.SECONDS

import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import io.reactivex.Flowable
import test.mqtt.MqttBrokerTest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.function.Consumer

class HiveMqttTest extends MqttBrokerTest {
  def 'publish #api and #subscription callback at QoS #qos preserve payload and parentage'() {
    setup:
    def subscriber = client('consumer')
    def producer = client('producer')
    def received = new CompletableFuture<byte[]>()
    def executor = Executors.newSingleThreadExecutor()
    Consumer<Mqtt3Publish> callback = { Mqtt3Publish incoming ->
      runUnderTrace('business') {}
      received.complete(incoming.payloadAsBytes)
    }
    if (subscription) {
      subscriber.subscribeWith().topicFilter('test/messages').qos(MqttQos.fromCode(qos))
        .callback(callback).executor(executor).send().get(10, SECONDS)
    } else {
      subscriber.publishes(MqttGlobalPublishFilter.ALL, callback)
      subscriber.subscribeWith().topicFilter('test/messages').qos(MqttQos.fromCode(qos)).send().get(10, SECONDS)
    }
    byte[] bytes = [0, -1, 42, 0] as byte[]
    def message = Mqtt3Publish.builder().topic('test/messages').qos(MqttQos.fromCode(qos)).payload(bytes).build()

    when:
    runUnderTrace('request') {
      if (api == 'blocking') {
        producer.toBlocking().publish(message)
      }
      else if (api == 'async') {
        producer.publish(message).get(10, SECONDS)
      }
      else {
        producer.toRx().publish(Flowable.just(message)).blockingSubscribe()
      }
    }

    then:
    received.get(10, SECONDS) == bytes
    message.payloadAsBytes == bytes
    assertTraces(2) {
      trace(2) {
        sortSpansByStart()
        basicSpan(it, 'request')
        mqttSpan(it, 'mqtt.publish', 'hivemq-mqtt', 'test/messages', 'producer', qos, span(0))
      }
      trace(2) {
        sortSpansByStart()
        mqttSpan(it, 'mqtt.consume', 'hivemq-mqtt', 'test/messages', 'consumer', qos)
        basicSpan(it, 'business', span(0))
      }
    }

    cleanup:
    producer?.disconnect()?.get(10, SECONDS)
    subscriber?.disconnect()?.get(10, SECONDS)
    executor.shutdownNow()

    where:
    [api, qos, subscription] << [['blocking', 'async', 'reactive'], [0, 1, 2], [true, false]].combinations()
  }

  def 'reactive subscription keeps the suback and consumer root context'() {
    setup:
    def subscriber = client('rx-consumer')
    def producer = client('rx-producer')
    def received = new CompletableFuture<byte[]>()
    def stream = subscriber.toRx().subscribePublishesWith().topicFilter('test/reactive').qos(MqttQos.AT_LEAST_ONCE).applySubscribe()
    def ready = stream.subscribeSingleFuture({ Mqtt3Publish message ->
      runUnderTrace('business') {}
      received.complete(message.payloadAsBytes)
    } as io.reactivex.functions.Consumer)
    ready.get(10, SECONDS)

    when:
    producer.publish(Mqtt3Publish.builder().topic('test/reactive').qos(MqttQos.AT_LEAST_ONCE).payload([7] as byte[]).build()).get(10, SECONDS)

    then:
    received.get(10, SECONDS) == ([7] as byte[])
    assertTraces(2) {
      trace(1) { mqttSpan(it, 'mqtt.publish', 'hivemq-mqtt', 'test/reactive', 'rx-producer', 1) }
      trace(2) {
        sortSpansByStart()
        mqttSpan(it, 'mqtt.consume', 'hivemq-mqtt', 'test/reactive', 'rx-consumer', 1)
        basicSpan(it, 'business', span(0))
      }
    }

    cleanup:
    ready?.cancel(false)
    producer?.disconnect()?.get(10, SECONDS)
    subscriber?.disconnect()?.get(10, SECONDS)
  }

  def 'blocking receives create spans only when a message is returned'() {
    setup:
    def subscriber = client('pull-consumer')
    def producer = client('pull-producer')
    def publishes = subscriber.toBlocking().publishes(MqttGlobalPublishFilter.ALL)
    subscriber.subscribeWith().topicFilter('test/pull').send().get(10, SECONDS)
    assert !publishes.receiveNow().present

    when:
    producer.publish(Mqtt3Publish.builder().topic('test/pull').qos(MqttQos.AT_LEAST_ONCE).payload([1] as byte[]).build()).get(10, SECONDS)
    def message = publishes.receive(10, SECONDS).get()

    then:
    message.payloadAsBytes == ([1] as byte[])
    assertTraces(2) {
      trace(1) { mqttSpan(it, 'mqtt.publish', 'hivemq-mqtt', 'test/pull', 'pull-producer', 1) }
      trace(1) { mqttSpan(it, 'mqtt.consume', 'hivemq-mqtt', 'test/pull', 'pull-consumer', 1) }
    }

    cleanup:
    publishes?.close()
    producer?.disconnect()?.get(10, SECONDS)
    subscriber?.disconnect()?.get(10, SECONDS)
  }

  def 'failed publish reports the original error'() {
    setup:
    def producer = Mqtt3Client.builder().identifier('disconnected').serverHost('127.0.0.1').serverPort(broker.port).buildAsync()

    when:
    producer.publish(Mqtt3Publish.builder().topic('test/error').qos(MqttQos.AT_LEAST_ONCE).payload([1] as byte[]).build()).get(10, SECONDS)

    then:
    def error = thrown(java.util.concurrent.ExecutionException)
    assertTraces(1) {
      trace(1) { mqttSpan(it, 'mqtt.publish', 'hivemq-mqtt', 'test/error', 'disconnected', 1, null, error.cause) }
    }
  }

  def 'callback failures are recorded on the consumer span'() {
    setup:
    def subscriber = client('failing-consumer')
    def producer = client('producer')
    def received = new CompletableFuture<Void>()
    def failure = new IllegalStateException('consumer failure')
    subscriber.subscribeWith().topicFilter('test/callback-error').qos(MqttQos.AT_LEAST_ONCE)
      .callback({ Mqtt3Publish incoming ->
        received.completeExceptionally(failure)
        throw failure
      } as Consumer).send().get(10, SECONDS)

    when:
    producer.publish(Mqtt3Publish.builder().topic('test/callback-error').qos(MqttQos.AT_LEAST_ONCE).build()).get(10, SECONDS)
    received.get(10, SECONDS)

    then:
    def error = thrown(java.util.concurrent.ExecutionException)
    error.cause.is(failure)
    assertTraces(2) {
      trace(1) { mqttSpan(it, 'mqtt.publish', 'hivemq-mqtt', 'test/callback-error', 'producer', 1) }
      trace(1) { mqttSpan(it, 'mqtt.consume', 'hivemq-mqtt', 'test/callback-error', 'failing-consumer', 1, null, failure) }
    }

    cleanup:
    producer?.disconnect()?.get(10, SECONDS)
    subscriber?.disconnect()?.get(10, SECONDS)
  }

  def 'MQTT 5 clients do not match the MQTT 3 integration'() {
    setup:
    def producer = com.hivemq.client.mqtt.mqtt5.Mqtt5Client.builder().serverHost('127.0.0.1').serverPort(broker.port).buildAsync()

    when:
    producer.publishWith().topic('test/mqtt5').send().get(10, SECONDS)

    then:
    thrown(java.util.concurrent.ExecutionException)
    assertTraces(0) {}
  }

  private def client(String id) {
    def client = Mqtt3Client.builder().identifier(id).serverHost('127.0.0.1').serverPort(broker.port).buildAsync()
    client.connect().get(10, SECONDS)
    return client
  }
}
