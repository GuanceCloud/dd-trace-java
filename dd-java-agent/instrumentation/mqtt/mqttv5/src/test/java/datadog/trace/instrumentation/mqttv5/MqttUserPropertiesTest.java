package datadog.trace.instrumentation.mqttv5;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.mqttv5.MqttDecorator.PUBLISH_DECORATOR;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import datadog.trace.agent.test.AbstractInstrumentationTest;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import org.junit.jupiter.api.Test;

class MqttUserPropertiesTest extends AbstractInstrumentationTest {
  @Test
  void preservesBusinessPropertiesAndDoesNotMutateTheOriginalList() {
    UserProperty first = new UserProperty("business-key", "first"),
        second = new UserProperty("business-key", "second");
    List<UserProperty> original = Collections.unmodifiableList(Arrays.asList(first, second));
    MqttProperties properties = new MqttProperties();
    properties.setUserProperties(original);
    MqttMessage message = new MqttMessage(new byte[] {1, 2});
    message.setProperties(properties);
    AgentSpan span = PUBLISH_DECORATOR.createSpan("topic", message);
    span.finish();
    assertSame(first, message.getProperties().getUserProperties().get(0));
    assertSame(second, message.getProperties().getUserProperties().get(1));
    assertEquals(2, original.size());
    assertEquals(2, message.getPayload().length);
  }

  @Test
  void repeatedPublishingReplacesTracingHeadersWithoutAccumulatingDuplicates() {
    MqttMessage message = new MqttMessage(new byte[] {1});
    MqttProperties properties = new MqttProperties();
    properties.setUserProperties(Collections.singletonList(new UserProperty("business", "keep")));
    message.setProperties(properties);
    AgentSpan first = PUBLISH_DECORATOR.createSpan("topic", message);
    first.finish();
    AgentSpan second = PUBLISH_DECORATOR.createSpan("topic", message);
    second.finish();
    List<UserProperty> values = message.getProperties().getUserProperties();
    assertEquals(1, values.stream().filter(p -> p.getKey().equals("business")).count());
    assertEquals(1, values.stream().filter(p -> p.getKey().equals("x-datadog-parent-id")).count());
    assertEquals(
        Long.toUnsignedString(second.getSpanId()),
        values.stream()
            .filter(p -> p.getKey().equals("x-datadog-parent-id"))
            .findFirst()
            .get()
            .getValue());
  }

  @Test
  void supportsMessagesWithoutUserProperties() {
    MqttMessage message = new MqttMessage(new byte[] {1});
    AgentSpan span = PUBLISH_DECORATOR.createSpan("topic", message);
    span.finish();
    assertNotNull(message.getProperties());
    assertNotNull(message.getProperties().getUserProperties());
  }

  @Test
  void aNewParentDoesNotRepublishStaleBaggage() {
    MqttMessage message = new MqttMessage(new byte[] {1});
    MqttProperties properties = new MqttProperties();
    properties.setUserProperties(
        Collections.singletonList(new UserProperty("ot-baggage-business", "application")));
    message.setProperties(properties);
    for (int i = 1; i <= 2; i++) {
      AgentSpan parent = startSpan("test", "parent" + i);
      parent.setBaggageItem("old-" + i, "value-" + i);
      try (datadog.context.ContextScope ignored = activateSpan(parent)) {
        PUBLISH_DECORATOR.createSpan("topic", message).finish();
      } finally {
        parent.finish();
      }
    }
    List<UserProperty> values = message.getProperties().getUserProperties();
    assertEquals(0, values.stream().filter(v -> v.getKey().equals("ot-baggage-old-1")).count());
    assertEquals(1, values.stream().filter(v -> v.getKey().equals("ot-baggage-old-2")).count());
    assertEquals(
        1,
        values.stream()
            .filter(
                v -> v.getKey().equals("ot-baggage-business") && v.getValue().equals("application"))
            .count());
  }

  @Test
  void injectedPropertiesKeepTheOriginalMqttWireEncoding() throws Exception {
    MqttMessage message = new MqttMessage(new byte[] {1, 2, 3});
    MqttProperties properties = new MqttProperties();
    properties.setUserProperties(Collections.singletonList(new UserProperty("business", "keep")));
    message.setProperties(properties);
    PUBLISH_DECORATOR.createSpan("topic", message).finish();
    org.eclipse.paho.mqttv5.common.packet.MqttPublish packet =
        new org.eclipse.paho.mqttv5.common.packet.MqttPublish(
            "topic", message, message.getProperties());
    byte[] header = packet.getHeader(), payload = packet.getPayload();
    byte[] encoded = new byte[header.length + payload.length];
    System.arraycopy(header, 0, encoded, 0, header.length);
    System.arraycopy(payload, 0, encoded, header.length, payload.length);
    org.eclipse.paho.mqttv5.common.packet.MqttPublish decoded =
        (org.eclipse.paho.mqttv5.common.packet.MqttPublish)
            org.eclipse.paho.mqttv5.common.packet.MqttWireMessage.createWireMessage(encoded);
    assertArrayEquals(message.getPayload(), decoded.getMessage().getPayload());
    assertEquals(
        message.getProperties().getUserProperties().size(),
        decoded.getProperties().getUserProperties().size());
    for (int i = 0; i < message.getProperties().getUserProperties().size(); i++) {
      assertEquals(
          message.getProperties().getUserProperties().get(i).getKey(),
          decoded.getProperties().getUserProperties().get(i).getKey());
      assertEquals(
          message.getProperties().getUserProperties().get(i).getValue(),
          decoded.getProperties().getUserProperties().get(i).getValue());
    }
  }
}
