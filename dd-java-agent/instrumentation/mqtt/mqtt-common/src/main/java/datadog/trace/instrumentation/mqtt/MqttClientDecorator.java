package datadog.trace.instrumentation.mqtt;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;

import datadog.trace.api.cache.DDCache;
import datadog.trace.api.cache.DDCaches;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.AgentSpanContext;
import datadog.trace.bootstrap.instrumentation.api.InternalSpanTypes;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;
import datadog.trace.bootstrap.instrumentation.decorator.ClientDecorator;

/** Shared MQTT span names and broker identity; never reads or modifies message bodies. */
public abstract class MqttClientDecorator extends ClientDecorator {
  public static final CharSequence PUBLISH = UTF8BytesString.create("mqtt.publish");
  public static final CharSequence CONSUME = UTF8BytesString.create("mqtt.consume");
  private static final DDCache<String, MqttBrokerInfo> BROKERS = DDCaches.newFixedSizeCache(32);
  private final String instrumentation;
  private final CharSequence component;
  private final String kind;

  public MqttClientDecorator(String instrumentation, String kind) {
    this.instrumentation = instrumentation;
    this.component = UTF8BytesString.create(instrumentation);
    this.kind = kind;
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
    return component;
  }

  public AgentSpan start(
      String topic, int qos, boolean retained, String clientId, String serverUri) {
    return start(topic, qos, retained, clientId, serverUri, null);
  }

  public AgentSpan start(
      String topic,
      int qos,
      boolean retained,
      String clientId,
      String serverUri,
      AgentSpanContext parent) {
    boolean producer = Tags.SPAN_KIND_PRODUCER.equals(kind);
    CharSequence operation = producer ? PUBLISH : CONSUME;
    AgentSpan span =
        producer
            ? startSpan(instrumentation, operation)
            : startSpan(instrumentation, operation, parent);
    afterStart(span);
    span.setResourceName(operation + " " + topic);
    span.setTag("messaging.system", "mqtt");
    span.setTag("messaging.destination.name", topic);
    span.setTag("mqtt.topic", topic);
    span.setTag("mqtt.clientid", clientId);
    span.setTag("mqtt.message.qos", qos);
    span.setTag("mqtt.message.retained", retained);
    if (serverUri != null) {
      BROKERS.computeIfAbsent(serverUri, MqttBrokerInfo::parse).decorate(span);
    }
    return span;
  }

  public void finish(AgentSpan span, Throwable error) {
    onError(span, error);
    beforeFinish(span);
    span.finish();
  }
}
