package io.moquette.broker

import static java.nio.charset.StandardCharsets.UTF_8

import datadog.trace.agent.test.InstrumentationSpecification
import datadog.trace.bootstrap.instrumentation.api.Tags
import io.moquette.broker.config.MemoryConfig
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.mqtt.MqttFixedHeader
import io.netty.handler.codec.mqtt.MqttMessage
import io.netty.handler.codec.mqtt.MqttMessageType
import io.netty.handler.codec.mqtt.MqttPublishMessage
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader
import io.netty.handler.codec.mqtt.MqttQoS

class MoquetteConnectionTest extends InstrumentationSpecification {
  def "delivery attempts preserve duplicate flag #duplicate and payload"() {
    setup:
    def channel = new EmbeddedChannel()
    NettyUtils.clientID(channel, "subscriber")
    def properties = new Properties()
    properties.setProperty("immediate_buffer_flush", "true")
    def connection = new MQTTConnection(channel, new BrokerConfiguration(new MemoryConfig(properties)), null, null, null)
    def payload = Unpooled.copiedBuffer("payload", UTF_8)
    def message = new MqttPublishMessage(
      new MqttFixedHeader(MqttMessageType.PUBLISH, duplicate, MqttQoS.AT_LEAST_ONCE, false, 0),
      new MqttPublishVariableHeader("test/retry", 1), payload)

    when:
    connection.sendPublish(message)
    channel.flushOutbound()
    MqttPublishMessage sent = channel.readOutbound()

    then:
    sent.fixedHeader().isDup() == duplicate
    sent.payload().toString(UTF_8) == "payload"
    assertTraces(1) {
      trace(1) {
        span {
          operationName "moquette.deliver"
          resourceName "moquette.deliver test/retry"
          spanType "queue"
          parent()
          errored false
          tags {
            defaultTags()
            "$Tags.COMPONENT" "moquette"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_PRODUCER
            "messaging.system" "mqtt"
            "mqtt.topic" "test/retry"
            "mqtt.clientid" "subscriber"
            "mqtt.message.qos" 1
            "mqtt.message.retained" false
            "mqtt.message.duplicate" duplicate
          }
        }
      }
    }

    cleanup:
    if (sent != null && sent.refCnt() > 0) {
      sent.release()
    }
    if (payload.refCnt() > 0) {
      payload.release()
    }
    channel.finishAndReleaseAll()

    where:
    duplicate << [false, true]
  }

  def "control packets do not create message delivery spans"() {
    setup:
    def channel = new EmbeddedChannel()
    def connection = new MQTTConnection(channel, new BrokerConfiguration(new MemoryConfig(new Properties())), null, null, null)

    when:
    connection.sendIfWritableElseDrop(new MqttMessage(
      new MqttFixedHeader(MqttMessageType.PINGRESP, false, MqttQoS.AT_MOST_ONCE, false, 0)))
    channel.flushOutbound()

    then:
    channel.readOutbound() != null
    assertTraces(0) {}

    cleanup:
    channel.finishAndReleaseAll()
  }
}
