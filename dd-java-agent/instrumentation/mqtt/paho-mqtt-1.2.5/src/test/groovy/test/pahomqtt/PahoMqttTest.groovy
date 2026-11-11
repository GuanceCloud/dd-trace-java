package test.pahomqtt

import static datadog.trace.agent.test.utils.TraceUtils.basicSpan
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import static java.nio.charset.StandardCharsets.UTF_8
import static java.util.concurrent.TimeUnit.SECONDS
import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.currentSpan
import datadog.trace.bootstrap.instrumentation.api.Tags
import java.util.concurrent.CompletableFuture
import java.nio.ByteBuffer
import datadog.trace.instrumentation.mqtt.payload.MqttPayload
import org.eclipse.paho.client.mqttv3.IMqttMessageListener
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.internal.ClientComms
import org.eclipse.paho.client.mqttv3.internal.CommsCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class PahoMqttTest extends PahoBrokerTest {
  def "publish and callback tracing preserve payload at QoS #qos"() {
    setup:
    def received = new CompletableFuture<MqttMessage>()
    def subscriber = client("consumer-" + qos, received) { runUnderTrace("business") {} }
    def producer = client("producer-" + qos)
    def topic = "client/qos/" + qos
    subscriber.subscribe(topic, qos)
    byte[] bytes = [0, -1, 7, 0] as byte[]
    def message = new MqttMessage(bytes)
    message.qos = qos

    when:
    producer.publish(topic, message)

    then:
    received.get(10, SECONDS).payload == bytes
    message.payload == bytes
    assertTraces(2) {
      trace(1) { clientSpan(it, "mqtt.publish", topic, "producer-" + qos, qos) }
      trace(2) {
        sortSpansByStart()
        clientSpan(it, "mqtt.consume", topic, "consumer-" + qos, qos)
        basicSpan(it, "business", span(0))
      }
    }

    cleanup:
    close(producer)
    close(subscriber)

    where:
    qos << [0, 1, 2]
  }

  def "publishing while disconnected records error and closes scope"() {
    setup:
    def producer = new MqttClient("tcp://127.0.0.1:" + broker.port, "disconnected", new MemoryPersistence())

    when:
    producer.publish("client/error", new MqttMessage("test".getBytes(UTF_8)))

    then:
    def error = thrown(MqttException)
    assertTraces(1) {
      trace(1) {
        span {
          operationName "mqtt.publish"
          resourceName "mqtt.publish client/error"
          spanType "queue"
          parent()
          errored true
          tags {
            defaultTagsWithExplicitPeerService()
            "$Tags.COMPONENT" "paho-mqtt"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_PRODUCER
            "messaging.system" "mqtt"
            "messaging.destination.name" "client/error"
            "peer.hostname" "127.0.0.1"
            "peer.port" broker.port
            "peer.service" "127.0.0.1:" + broker.port
            "mqtt.topic" "client/error"
            "mqtt.clientid" "disconnected"
            "mqtt.serverURI" "tcp://127.0.0.1:" + broker.port
            "mqtt.message.qos" 1
            "mqtt.message.retained" false
            "mqtt.message.duplicate" false
            errorTags(MqttException, error.message)
          }
        }
      }
    }

    cleanup:
    close(producer)
  }

  def "per subscription message listeners are traced"() {
    setup:
    def received = new CompletableFuture<MqttMessage>()
    def subscriber = client("listener-consumer")
    def producer = client("listener-producer")
    def topic = "client/listener"
    subscriber.subscribe(topic, 1, { String name, MqttMessage message ->
      runUnderTrace("listener-business") {}
      received.complete(message)
    } as IMqttMessageListener)

    when:
    producer.publish(topic, [1, 2, 3] as byte[], 1, false)

    then:
    received.get(10, SECONDS).payload == ([1, 2, 3] as byte[])
    assertTraces(2) {
      trace(1) { clientSpan(it, "mqtt.publish", topic, "listener-producer", 1) }
      trace(2) {
        sortSpansByStart()
        clientSpan(it, "mqtt.consume", topic, "listener-consumer", 1)
        basicSpan(it, "listener-business", span(0))
      }
    }

    cleanup:
    close(producer)
    close(subscriber)
  }

  def "async completion preserves the original listener and parent context"() {
    setup:
    def uri = "tcp://127.0.0.1:" + broker.port
    def producer = new MqttAsyncClient(uri, "async-producer", new MemoryPersistence())
    producer.connect().waitForCompletion(10000)
    def completed = new CompletableFuture<Boolean>()
    def listener = new IMqttActionListener() {
        @Override void onSuccess(IMqttToken completedToken) {
          runUnderTrace("completion-business") {}
          completed.complete(true)
        }
        @Override void onFailure(IMqttToken completedToken, Throwable error) {
          completed.completeExceptionally(error)
        }
      }
    def token

    when:
    runUnderTrace("publish-request") {
      token = producer.publish("client/async", new MqttMessage([1, 2] as byte[]), null, listener)
    }

    then:
    completed.get(10, SECONDS)
    token.actionCallback.is(listener)
    assertTraces(1) {
      trace(3) {
        sortSpansByStart()
        basicSpan(it, "publish-request")
        clientSpan(it, "mqtt.publish", "client/async", "async-producer", 1, span(0))
        basicSpan(it, "completion-business", span(1))
      }
    }

    cleanup:
    if (producer.connected) {
      producer.disconnect().waitForCompletion(10000)
    }
    producer.close()
  }

  def "disabled propagation preserves an already framed business payload"() {
    setup:
    byte[] framed
    runUnderTrace("frame-origin") {
      framed = MqttPayload.encode(currentSpan(), ByteBuffer.wrap([0, -1, 7] as byte[]))
    }
    TEST_WRITER.clear()
    def received = new CompletableFuture<MqttMessage>()
    def subscriber = client("framed-consumer", received)
    def producer = client("framed-producer")
    subscriber.subscribe("client/framed", 1)

    when:
    producer.publish("client/framed", framed, 1, false)

    then:
    received.get(10, SECONDS).payload == framed
    assertTraces(2) {
      trace(1) { clientSpan(it, "mqtt.publish", "client/framed", "framed-producer", 1) }
      trace(1) { clientSpan(it, "mqtt.consume", "client/framed", "framed-consumer", 1) }
    }

    cleanup:
    close(producer)
    close(subscriber)
  }

  def "consumer exceptions are recorded without leaving a scope open"() {
    setup:
    def client = Stub(IMqttAsyncClient) {
      getClientId() >> "failing-consumer"
      getServerURI() >> "tcp://127.0.0.1:" + broker.port
    }
    def comms = Stub(ClientComms) { getClient() >> client }
    def callback = new CommsCallback(comms)
    def failure = new IllegalStateException("consumer failure")
    callback.setCallback(new MqttCallback() {
        @Override void connectionLost(Throwable error) {}
        @Override void deliveryComplete(IMqttDeliveryToken token) {}
        @Override void messageArrived(String topic, MqttMessage message) {
          throw failure
        }
      })

    when:
    callback.deliverMessage("client/callback-error", 1, new MqttMessage([1, 2] as byte[]))

    then:
    def error = thrown(IllegalStateException)
    error.is(failure)
    assertTraces(1) {
      trace(1) {
        clientSpan(it, "mqtt.consume", "client/callback-error", "failing-consumer", 1, null, failure)
      }
    }
  }

  private void clientSpan(def trace, String operation, String topic, String clientId, int qos, def parentSpan = null, Throwable failure = null) {
    trace.span {
      operationName operation
      resourceName operation + " " + topic
      spanType "queue"
      if (parentSpan == null) {
        parent()
      } else {
        childOf parentSpan
      }
      errored (failure != null)
      tags {
        defaultTagsWithExplicitPeerService()
        "$Tags.COMPONENT" "paho-mqtt"
        "$Tags.SPAN_KIND" (operation == "mqtt.publish" ? Tags.SPAN_KIND_PRODUCER : Tags.SPAN_KIND_CONSUMER)
        "messaging.system" "mqtt"
        "messaging.destination.name" topic
        "peer.hostname" "127.0.0.1"
        "peer.port" broker.port
        "peer.service" "127.0.0.1:" + broker.port
        "mqtt.topic" topic
        "mqtt.clientid" clientId
        "mqtt.serverURI" "tcp://127.0.0.1:" + broker.port
        "mqtt.message.qos" qos
        "mqtt.message.retained" false
        "mqtt.message.duplicate" false
        if (failure != null) {
          errorTags(failure.class, failure.message)
        }
      }
    }
  }
}
