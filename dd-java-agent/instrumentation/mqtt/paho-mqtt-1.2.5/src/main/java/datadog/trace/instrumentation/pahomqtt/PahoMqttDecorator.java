package datadog.trace.instrumentation.pahomqtt;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;

import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.AgentSpanContext;
import datadog.trace.bootstrap.instrumentation.api.InternalSpanTypes;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;
import datadog.trace.bootstrap.instrumentation.decorator.ClientDecorator;
import datadog.trace.instrumentation.mqtt.payload.MqttPayload;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class PahoMqttDecorator extends ClientDecorator {
  public static final CharSequence PUBLISH = UTF8BytesString.create("mqtt.publish");
  public static final CharSequence CONSUME = UTF8BytesString.create("mqtt.consume");
  public static final CharSequence COMPONENT = UTF8BytesString.create("paho-mqtt");
  public static final PahoMqttDecorator PRODUCER = new PahoMqttDecorator(Tags.SPAN_KIND_PRODUCER);
  public static final PahoMqttDecorator CONSUMER = new PahoMqttDecorator(Tags.SPAN_KIND_CONSUMER);
  private final String kind;

  private PahoMqttDecorator(String kind) {
    this.kind = kind;
  }

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"paho-mqtt"};
  }

  @Override
  protected String service() {
    return null;
  }

  @Override
  protected String spanKind() {
    return kind;
  }

  @Override
  protected CharSequence spanType() {
    return InternalSpanTypes.MESSAGE_CLIENT;
  }

  @Override
  protected CharSequence component() {
    return COMPONENT;
  }

  public AgentSpan start(String topic, MqttMessage message, String clientId, String serverUri) {
    return start(topic, message, clientId, serverUri, null);
  }

  public AgentSpan start(
      String topic,
      MqttMessage message,
      String clientId,
      String serverUri,
      MqttPayload.Decoded decoded) {
    CharSequence operation = this == PRODUCER ? PUBLISH : CONSUME;
    AgentSpan span;
    if (this == CONSUMER) {
      AgentSpanContext parent = decoded == null ? null : decoded.parent();
      span = startSpan("paho-mqtt", operation, parent);
    } else {
      span = startSpan("paho-mqtt", operation);
    }
    afterStart(span);
    span.setResourceName(operation + " " + topic);
    span.setTag("messaging.system", "mqtt");
    span.setTag("mqtt.topic", topic);
    span.setTag("mqtt.clientid", clientId);
    span.setTag("mqtt.serverURI", serverUri);
    span.setTag("mqtt.message.qos", message.getQos());
    span.setTag("mqtt.message.retained", message.isRetained());
    span.setTag("mqtt.message.duplicate", message.isDuplicate());
    return span;
  }

  public void finish(AgentSpan span, Throwable error) {
    onError(span, error);
    beforeFinish(span);
    span.finish();
  }
}
