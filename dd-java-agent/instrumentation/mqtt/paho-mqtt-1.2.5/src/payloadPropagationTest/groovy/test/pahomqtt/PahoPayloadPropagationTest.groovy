package test.pahomqtt

import static datadog.trace.agent.test.utils.TraceUtils.basicSpan
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import static java.util.concurrent.TimeUnit.SECONDS
import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.currentSpan
import datadog.trace.instrumentation.mqtt.payload.MqttPayload
import java.nio.ByteBuffer
import datadog.trace.bootstrap.instrumentation.api.Tags
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import io.moquette.interception.AbstractInterceptHandler
import io.moquette.interception.messages.InterceptPublishMessage
import io.moquette.broker.Server
import spock.lang.Shared
import org.eclipse.paho.client.mqttv3.MqttMessage

class PahoPayloadPropagationTest extends PahoBrokerTest {
  @Shared PayloadHandler callback = new PayloadHandler()

  @Override protected List interceptHandlers() {
    return [callback]
  }

  @Override void configurePreAgent() {
    super.configurePreAgent()
    injectSysConfig("trace.mqtt.payload.propagation.enabled", "true")
  }

  def "producer broker and consumer share one trace at QoS #qos with literal frame #literalFrame"() {
    setup:
    def received = new CompletableFuture<MqttMessage>()
    def subscriber = client("consumer-" + qos, received) { runUnderTrace("consumer-business") {} }
    def producer = client("producer-" + qos)
    def topic = "propagation/qos/" + qos + "/" + literalFrame
    subscriber.subscribe(topic, qos)
    byte[] bytes = [0, -1, 7, 0, 42] as byte[]
    if (literalFrame) {
      runUnderTrace("literal-frame") { bytes = MqttPayload.encode(currentSpan(), ByteBuffer.wrap(bytes)) }
      TEST_WRITER.clear()
    }
    def message = new MqttMessage(bytes)
    message.qos = qos

    when:
    producer.publish(topic, message)

    then:
    received.get(10, SECONDS).payload == bytes
    message.payload == bytes
    callback.payloads.poll(10, SECONDS) == bytes
    TEST_WRITER.waitForTraces(3)
    def spans = TEST_WRITER.flatten()
    def publish = spans.find { it.operationName.toString() == "mqtt.publish" }
    def receive = spans.find { it.operationName.toString() == "moquette.receive" }
    def deliver = spans.find { it.operationName.toString() == "moquette.deliver" }
    def consume = spans.find { it.operationName.toString() == "mqtt.consume" }
    spans*.traceId.toSet().size() == 1
    receive.parentId == publish.spanId
    deliver.parentId == receive.spanId
    consume.parentId == receive.spanId
    assertTraces(3) {
      trace(1) {
        endpointSpan(it, "mqtt.publish", topic, "producer-" + qos, qos, null, false)
      }
      trace(3) {
        sortSpansByStart()
        endpointSpan(it, "moquette.receive", topic, "producer-" + qos, qos, publish, true)
        endpointSpan(it, "moquette.deliver", topic, "consumer-" + qos, qos, span(0), false)
        endpointSpan(it, "moquette.on_publish", topic, "producer-" + qos, qos, span(0), false)
      }
      trace(2) {
        sortSpansByStart()
        endpointSpan(it, "mqtt.consume", topic, "consumer-" + qos, qos, receive, true)
        basicSpan(it, "consumer-business", span(0))
      }
    }

    cleanup:
    close(producer)
    close(subscriber)

    where:
    qos | literalFrame
    0   | false
    1   | false
    2   | false
    1   | true
  }

  def "retained replay links back to the original broker receive span"() {
    setup:
    def producer = client("replay-producer")
    def topic = "propagation/retained-replay"
    byte[] bytes = [0, -1, 42] as byte[]

    when:
    producer.publish(topic, bytes, 1, true)

    then:
    callback.payloads.poll(10, SECONDS) == bytes
    TEST_WRITER.waitForTraces(2)
    def original = TEST_WRITER.flatten().find { it.operationName.toString() == "moquette.receive" }
    def publish = TEST_WRITER.flatten().find { it.operationName.toString() == "mqtt.publish" }
    assertTraces(2) {
      trace(1) { endpointSpan(it, "mqtt.publish", topic, "replay-producer", 1, null, false, true) }
      trace(2) {
        sortSpansByStart()
        endpointSpan(it, "moquette.receive", topic, "replay-producer", 1, publish, true, true)
        endpointSpan(it, "moquette.on_publish", topic, "replay-producer", 1, span(0), false, true)
      }
    }

    when:
    TEST_WRITER.clear()
    def received = new CompletableFuture<MqttMessage>()
    def subscriber = client("replay-consumer", received)
    subscriber.subscribe(topic, 1)

    then:
    received.get(10, SECONDS).payload == bytes
    assertTraces(2) {
      trace(1) { endpointSpan(it, "moquette.deliver", topic, "replay-consumer", 1, original, true, true) }
      trace(1) { endpointSpan(it, "mqtt.consume", topic, "replay-consumer", 1, original, true, true) }
    }

    cleanup:
    close(producer)
    close(subscriber)
  }

  def "empty retained payload still deletes retained messages"() {
    setup:
    def producer = client("retained-producer")
    def topic = "propagation/retained-delete"
    byte[] bytes = [0, -1, 42] as byte[]
    def field = Server.getDeclaredField("dispatcher")
    field.accessible = true
    def dispatcher = field.get(broker)
    def retainedField = dispatcher.class.getDeclaredField("retainedRepository")
    retainedField.accessible = true
    def repository = retainedField.get(dispatcher)

    when:
    producer.publish(topic, bytes, 1, true)

    then:
    callback.payloads.poll(10, SECONDS) == bytes
    TEST_WRITER.waitForTraces(2)
    repository.retainedOnTopic(topic).size() == 1
    def publish = TEST_WRITER.flatten().find { it.operationName.toString() == "mqtt.publish" }
    assertTraces(2) {
      trace(1) { endpointSpan(it, "mqtt.publish", topic, "retained-producer", 1, null, false, true) }
      trace(2) {
        sortSpansByStart()
        endpointSpan(it, "moquette.receive", topic, "retained-producer", 1, publish, true, true)
        endpointSpan(it, "moquette.on_publish", topic, "retained-producer", 1, span(0), false, true)
      }
    }

    when:
    TEST_WRITER.clear()
    producer.publish(topic, new byte[0], 1, true)

    then:
    callback.payloads.poll(10, SECONDS) == new byte[0]
    TEST_WRITER.waitForTraces(2)
    repository.retainedOnTopic(topic).isEmpty()
    assertTraces(2) {
      trace(1) { endpointSpan(it, "mqtt.publish", topic, "retained-producer", 1, null, false, true) }
      trace(2) {
        sortSpansByStart()
        endpointSpan(it, "moquette.receive", topic, "retained-producer", 1, null, false, true)
        endpointSpan(it, "moquette.on_publish", topic, "retained-producer", 1, span(0), false, true)
      }
    }

    cleanup:
    close(producer)
  }

  static class PayloadHandler extends AbstractInterceptHandler {
    final LinkedBlockingQueue<byte[]> payloads = new LinkedBlockingQueue<>()
    @Override String getID() {
      return "payload-observer"
    }
    @Override void onPublish(InterceptPublishMessage message) {
      byte[] bytes = new byte[message.payload.readableBytes()]
      message.payload.getBytes(message.payload.readerIndex(), bytes)
      payloads.add(bytes)
      super.onPublish(message)
    }
  }

  private void endpointSpan(def trace, String operation, String topic, String clientId,
    int qos, def parentSpan, boolean distributed, boolean retained = false) {
    trace.span {
      operationName operation
      resourceName operation + " " + topic
      spanType "queue"
      errored false
      if (parentSpan == null) {
        parent()
      } else {
        childOf parentSpan
      }
      tags {
        if (operation.startsWith("moquette.")) {
          defaultTags(distributed)
        } else {
          defaultTagsWithExplicitPeerService(distributed)
        }
        "$Tags.COMPONENT" (operation.startsWith("moquette.") ? "moquette" : "paho-mqtt")
        "$Tags.SPAN_KIND" (operation == "moquette.receive" ? Tags.SPAN_KIND_SERVER :
          (operation == "mqtt.consume" || operation == "moquette.on_publish") ? Tags.SPAN_KIND_CONSUMER : Tags.SPAN_KIND_PRODUCER)
        if (operation == "moquette.on_publish") {
          "async_entry" true
        }
        "messaging.system" "mqtt"
        if (!operation.startsWith("moquette.")) {
          "messaging.destination.name" topic
          "peer.hostname" "127.0.0.1"
          "peer.port" broker.port
          "peer.service" "127.0.0.1:" + broker.port
        }
        "mqtt.topic" topic
        "mqtt.clientid" clientId
        if (!operation.startsWith("moquette.")) {
          "mqtt.serverURI" "tcp://127.0.0.1:" + broker.port
        }
        "mqtt.message.qos" qos
        "mqtt.message.retained" retained
        "mqtt.message.duplicate" false
      }
    }
  }
}
