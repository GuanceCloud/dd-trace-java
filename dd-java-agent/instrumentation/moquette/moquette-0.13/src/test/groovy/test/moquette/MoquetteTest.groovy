package test.moquette

import static datadog.trace.agent.test.utils.TraceUtils.basicSpan
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import static java.nio.charset.StandardCharsets.UTF_8
import static java.util.concurrent.TimeUnit.SECONDS

import datadog.trace.agent.test.InstrumentationSpecification
import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.bootstrap.instrumentation.api.Tags
import io.moquette.broker.Server
import io.moquette.broker.config.MemoryConfig
import io.moquette.interception.AbstractInterceptHandler
import io.moquette.interception.messages.InterceptPublishMessage
import io.netty.buffer.Unpooled
import io.netty.handler.codec.mqtt.MqttFixedHeader
import io.netty.handler.codec.mqtt.MqttMessageType
import io.netty.handler.codec.mqtt.MqttPublishMessage
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader
import io.netty.handler.codec.mqtt.MqttQoS
import java.util.concurrent.CompletableFuture
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import spock.lang.Shared

class MoquetteTest extends InstrumentationSpecification {
  @Shared Server broker
  @Shared TestHandler handler = new TestHandler()

  def setupSpec() {
    def properties = new Properties()
    properties.setProperty("host", "127.0.0.1")
    properties.setProperty("port", "0")
    properties.setProperty("websocket_port", "disabled")
    properties.setProperty("persistent_store", "")
    properties.setProperty("persistence_enabled", "false")
    properties.setProperty("allow_anonymous", "true")
    properties.setProperty("netty.epoll", "false")
    broker = new Server()
    broker.startServer(new MemoryConfig(properties), [handler])
  }

  def cleanupSpec() {
    broker?.stopServer()
  }

  def "receives routes and invokes application callback at QoS #qos"() {
    setup:
    def topic = "test/qos/" + qos
    def received = new CompletableFuture<String>()
    def subscriber = client("subscriber-" + qos, received)
    def publisher = client("publisher-" + qos)
    subscriber.subscribe(topic, qos)

    when:
    publisher.publish(topic, "payload".getBytes(UTF_8), qos, false)

    then:
    received.get(10, SECONDS) == "payload"
    assertTraces(1) {
      trace(4) {
        sortSpansByStart()
        mqttSpan(it, "moquette.receive", topic, "publisher-" + qos, qos, false, false, null)
        mqttSpan(it, "moquette.deliver", topic, "subscriber-" + qos, qos, false, false, span(0))
        mqttSpan(it, "moquette.on_publish", topic, "publisher-" + qos, qos, false, false, span(0))
        basicSpan(it, "application", span(2))
      }
    }

    cleanup:
    close(publisher)
    close(subscriber)

    where:
    qos << [0, 1, 2]
  }

  def "embedded publishing preserves the business parent"() {
    setup:
    def topic = "test/internal"
    def received = new CompletableFuture<String>()
    def subscriber = client("embedded-subscriber", received)
    subscriber.subscribe(topic, 1)
    def message = publishMessage(topic, 1, false)

    when:
    runUnderTrace("business") {
      broker.internalPublish(message, "embedded")
    }

    then:
    received.get(10, SECONDS) == "payload"
    assertTraces(1) {
      trace(3) {
        sortSpansByStart()
        basicSpan(it, "business")
        mqttSpan(it, "moquette.publish", topic, null, 1, false, false, span(0))
        mqttSpan(it, "moquette.deliver", topic, "embedded-subscriber", 1, false, false, span(1))
      }
    }

    cleanup:
    close(subscriber)
    if (message.refCnt() > 0) {
      message.release()
    }
  }

  def "retained delivery is traced when a subscriber connects later"() {
    setup:
    def topic = "test/retained"
    def publisher = client("retained-publisher")

    when:
    publisher.publish(topic, "payload".getBytes(UTF_8), 1, true)

    then:
    assertTraces(1) {
      trace(3) {
        sortSpansByStart()
        mqttSpan(it, "moquette.receive", topic, "retained-publisher", 1, true, false, null)
        mqttSpan(it, "moquette.on_publish", topic, "retained-publisher", 1, true, false, span(0))
        basicSpan(it, "application", span(1))
      }
    }

    when:
    TEST_WRITER.clear()
    def received = new CompletableFuture<String>()
    def subscriber = client("retained-subscriber", received)
    subscriber.subscribe(topic, 1)

    then:
    received.get(10, SECONDS) == "payload"
    assertTraces(1) {
      trace(1) {
        mqttSpan(it, "moquette.deliver", topic, "retained-subscriber", 1, true, false, null)
      }
    }

    cleanup:
    close(subscriber)
    close(publisher)
  }

  def "callback exceptions close the scope and do not duplicate super callbacks"() {
    setup:
    def message = publishMessage("test/error", 1, false)
    def callback = new FailingHandler()

    when:
    callback.onPublish(new InterceptPublishMessage(message, "publisher", null))

    then:
    def error = thrown(IllegalStateException)
    error.message == "callback failure"
    assertTraces(1) {
      trace(1) {
        span {
          operationName "moquette.on_publish"
          spanType "queue"
          resourceName "moquette.on_publish test/error"
          errored true
          tags {
            defaultTags()
            "$Tags.COMPONENT" "moquette"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CONSUMER
            "messaging.system" "mqtt"
            "mqtt.topic" "test/error"
            "mqtt.clientid" "publisher"
            "mqtt.message.qos" 1
            "mqtt.message.retained" false
            "mqtt.message.duplicate" false
            errorTags(IllegalStateException, "callback failure")
          }
        }
      }
    }

    cleanup:
    if (message.refCnt() > 0) {
      message.release()
    }
  }

  private MqttClient client(String id, CompletableFuture<String> received = null) {
    def client = new MqttClient("tcp://127.0.0.1:" + broker.port, id, new MemoryPersistence())
    client.setCallback(new MqttCallback() {
        @Override void connectionLost(Throwable cause) {}
        @Override void deliveryComplete(IMqttDeliveryToken token) {}
        @Override void messageArrived(String topic, MqttMessage message) {
          received?.complete(new String(message.payload, UTF_8))
        }
      })
    client.connect()
    return client
  }

  private static void close(MqttClient client) {
    if (client != null) {
      if (client.connected) {
        client.disconnect()
      }
      client.close()
    }
  }

  private static MqttPublishMessage publishMessage(String topic, int qos, boolean retained) {
    return new MqttPublishMessage(
      new MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.valueOf(qos), retained, 0),
      new MqttPublishVariableHeader(topic, 1), Unpooled.copiedBuffer("payload", UTF_8))
  }

  private static void mqttSpan(TraceAssert trace, String operation, String topic, String clientId,
    int qos, boolean retained, boolean duplicate, def parentSpan) {
    trace.span {
      operationName operation
      spanType "queue"
      resourceName operation + " " + topic
      errored false
      if (parentSpan == null) {
        parent()
      } else {
        childOf parentSpan
      }
      tags {
        defaultTags()
        "$Tags.COMPONENT" "moquette"
        "$Tags.SPAN_KIND" (operation == "moquette.on_publish" ? Tags.SPAN_KIND_CONSUMER :
          operation == "moquette.receive" ? Tags.SPAN_KIND_SERVER : Tags.SPAN_KIND_PRODUCER)
        if (operation == "moquette.on_publish" && parentSpan != null) {
          "async_entry" true
        }
        "messaging.system" "mqtt"
        "mqtt.topic" topic
        if (clientId != null) {
          "mqtt.clientid" clientId
        }
        "mqtt.message.qos" qos
        "mqtt.message.retained" retained
        "mqtt.message.duplicate" duplicate
      }
    }
  }

  static class TestHandler extends AbstractInterceptHandler {
    @Override String getID() {
      return "test"
    }
    @Override void onPublish(InterceptPublishMessage message) {
      runUnderTrace("application") {}
      super.onPublish(message)
    }
  }

  static class FailingHandler extends AbstractInterceptHandler {
    @Override String getID() {
      return "failing"
    }
    @Override void onPublish(InterceptPublishMessage message) {
      super.onPublish(message)
      throw new IllegalStateException("callback failure")
    }
  }
}
