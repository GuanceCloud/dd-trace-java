package test.fusesourcemqtt

import static datadog.trace.agent.test.utils.TraceUtils.basicSpan
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import static java.util.concurrent.TimeUnit.SECONDS

import org.fusesource.mqtt.client.Callback
import org.fusesource.mqtt.client.BlockingConnection
import java.util.concurrent.CountDownLatch
import org.fusesource.mqtt.client.Listener
import org.fusesource.mqtt.client.MQTT
import org.fusesource.mqtt.client.QoS
import org.fusesource.mqtt.client.Topic
import org.fusesource.hawtbuf.Buffer
import org.fusesource.hawtbuf.UTF8Buffer
import test.mqtt.MqttBrokerTest
import java.util.concurrent.CompletableFuture

class FuseSourceMqttTest extends MqttBrokerTest {
  def 'publish #api and callback delivery at QoS #qos preserve bytes and parentage'() {
    setup:
    def received = new CompletableFuture<byte[]>()
    def subscriber = mqtt('consumer').callbackConnection()
    subscriber.listener(new Listener() {
        @Override void onConnected() {}
        @Override void onDisconnected() {}
        @Override void onFailure(Throwable error) {
          received.completeExceptionally(error)
        }
        @Override void onPublish(UTF8Buffer topic, Buffer payload, Runnable ack) {
          runUnderTrace('business') {}
          received.complete(payload.toByteArray())
          ack.run()
        }
      })
    def connected = new CompletableFuture<Void>()
    subscriber.connect(callback(connected))
    connected.get(10, SECONDS)
    def subscribed = new CompletableFuture<byte[]>()
    subscriber.dispatchQueue.execute {
      subscriber.subscribe([new Topic('test/messages', QoS.values()[qos])] as Topic[], callback(subscribed))
    }
    subscribed.get(10, SECONDS)
    def producer = mqtt('producer').futureConnection()
    await(producer.connect())
    byte[] bytes = [0, -1, 42, 0] as byte[]
    def completed = new CompletableFuture<Void>()
    def releasePublish = new CountDownLatch(1)
    if (api == 'future') {
      producer.dispatchQueue.execute { releasePublish.await(10, SECONDS) }
    }

    when:
    if (api == 'callback') {
      def direct = mqtt('direct').callbackConnection()
      def ready = new CompletableFuture<Void>()
      direct.connect(callback(ready))
      ready.get(10, SECONDS)
      direct.dispatchQueue.execute {
        runUnderTrace('request') {
          direct.publish('test/messages', bytes, QoS.values()[qos], false, callback(completed))
        }
      }
      completed.get(10, SECONDS)
      def closed = new CompletableFuture<Void>()
      direct.dispatchQueue.execute { direct.disconnect(callback(closed)) }
      closed.get(10, SECONDS)
    } else {
      runUnderTrace('request') {
        if (api == 'blocking') {
          new BlockingConnection(producer).publish('test/messages', bytes, QoS.values()[qos], false)
        } else {
          def future = producer.publish('test/messages', bytes, QoS.values()[qos], false)
          future.then(new Callback() {
              @Override void onSuccess(Object value) {
                runUnderTrace('completion') {}
                completed.complete(null)
              }
              @Override void onFailure(Throwable error) {
                completed.completeExceptionally(error)
              }
            })
          releasePublish.countDown()
        }
      }
      if (api == 'future') {
        completed.get(10, SECONDS)
      }
    }

    then:
    received.get(10, SECONDS) == bytes
    assertTraces(2) {
      trace(api == 'future' ? 3 : 2) {
        sortSpansByStart()
        basicSpan(it, 'request')
        mqttSpan(it, 'mqtt.publish', 'fusesource-mqtt', 'test/messages', api == 'callback' ? 'direct' : 'producer', qos, span(0))
        if (api == 'future') {
          basicSpan(it, 'completion', span(1))
        }
      }
      trace(2) {
        sortSpansByStart()
        mqttSpan(it, 'mqtt.consume', 'fusesource-mqtt', 'test/messages', 'consumer', qos)
        basicSpan(it, 'business', span(0))
      }
    }

    cleanup:
    releasePublish.countDown()
    if (producer != null) {
      await(producer.disconnect())
    }
    def closed = new CompletableFuture<Void>()
    subscriber?.dispatchQueue?.execute { subscriber.disconnect(callback(closed)) }
    closed.get(10, SECONDS)

    where:
    [api, qos] << [['blocking', 'future', 'callback'], [0, 1, 2]].combinations()
  }

  def 'blocking receive records an independent delivery span'() {
    setup:
    def subscriber = mqtt('pull-consumer').blockingConnection()
    subscriber.connect()
    subscriber.subscribe([new Topic('test/pull', QoS.AT_LEAST_ONCE)] as Topic[])
    def producer = mqtt('pull-producer').blockingConnection()
    producer.connect()

    when:
    producer.publish('test/pull', [1, 2, 3] as byte[], QoS.AT_LEAST_ONCE, false)
    def message = timedCall(subscriber, 'receive')
    message.ack()

    then:
    message.payload == ([1, 2, 3] as byte[])
    assertTraces(2) {
      trace(1) { mqttSpan(it, 'mqtt.publish', 'fusesource-mqtt', 'test/pull', 'pull-producer', 1) }
      trace(1) { mqttSpan(it, 'mqtt.consume', 'fusesource-mqtt', 'test/pull', 'pull-consumer', 1) }
    }

    cleanup:
    producer?.disconnect()
    subscriber?.disconnect()
  }

  def 'failed future publish records the original failure and closes its scope'() {
    setup:
    def producer = mqtt('disconnected').futureConnection()
    await(producer.connect())
    await(producer.disconnect())

    when:
    await(producer.publish('test/error', [1] as byte[], QoS.AT_LEAST_ONCE, false))

    then:
    def error = thrown(Exception)
    assertTraces(1) {
      trace(1) { mqttSpan(it, 'mqtt.publish', 'fusesource-mqtt', 'test/error', 'disconnected', 1, null, error) }
    }
  }

  def 'consumer callback failures are recorded before FuseSource closes the connection'() {
    setup:
    def received = new CompletableFuture<Void>()
    def failure = new IllegalStateException('consumer failure')
    def subscriber = mqtt('failing-consumer').callbackConnection()
    subscriber.listener(new Listener() {
        @Override void onConnected() {}
        @Override void onDisconnected() {}
        @Override void onFailure(Throwable error) {
          received.completeExceptionally(error)
        }
        @Override void onPublish(UTF8Buffer topic, Buffer payload, Runnable ack) {
          throw failure
        }
      })
    def connected = new CompletableFuture<Void>()
    subscriber.connect(callback(connected))
    connected.get(10, SECONDS)
    def subscribed = new CompletableFuture<byte[]>()
    subscriber.dispatchQueue.execute {
      subscriber.subscribe([new Topic('test/callback-error', QoS.AT_LEAST_ONCE)] as Topic[], callback(subscribed))
    }
    subscribed.get(10, SECONDS)
    def producer = mqtt('producer').blockingConnection()
    producer.connect()

    when:
    producer.publish('test/callback-error', [1] as byte[], QoS.AT_LEAST_ONCE, false)
    received.get(10, SECONDS)

    then:
    def caught = thrown(java.util.concurrent.ExecutionException)
    caught.cause.is(failure)
    assertTraces(2) {
      trace(1) { mqttSpan(it, 'mqtt.publish', 'fusesource-mqtt', 'test/callback-error', 'producer', 1) }
      trace(1) { mqttSpan(it, 'mqtt.consume', 'fusesource-mqtt', 'test/callback-error', 'failing-consumer', 1, null, failure) }
    }

    cleanup:
    producer?.disconnect()
  }

  private MQTT mqtt(String id) {
    def mqtt = new MQTT()
    mqtt.setHost('tcp://127.0.0.1:' + broker.port)
    mqtt.setClientId(id)
    mqtt.setVersion('3.1.1')
    return mqtt
  }

  private static Object await(Object future) {
    return timedCall(future, 'await')
  }

  /** The uber JAR uses backport TimeUnit instead of java.util.concurrent.TimeUnit. */
  private static Object timedCall(Object receiver, String name) {
    def method = receiver.class.methods.find { it.name == name && it.parameterCount == 2 }
    def seconds = method.parameterTypes[1].getField('SECONDS').get(null)
    try {
      return method.invoke(receiver, [10L, seconds] as Object[])
    } catch (java.lang.reflect.InvocationTargetException error) {
      throw error.cause
    }
  }

  private static Callback callback(CompletableFuture future) {
    return new Callback() {
        @Override void onSuccess(Object value) {
          future.complete(value)
        }
        @Override void onFailure(Throwable error) {
          future.completeExceptionally(error)
        }
      }
  }
}
