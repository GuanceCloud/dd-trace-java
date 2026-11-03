package datadog.trace.instrumentation.moquette;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.currentSpan;
import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;

import datadog.context.ContextScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.AgentSpanContext;
import datadog.trace.bootstrap.instrumentation.api.InternalSpanTypes;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;
import datadog.trace.bootstrap.instrumentation.decorator.ClientDecorator;
import datadog.trace.instrumentation.mqtt.payload.MqttPayload;
import io.moquette.interception.messages.InterceptPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;

public class MoquetteDecorator extends ClientDecorator {
  public static final MoquetteDecorator DECORATE = new MoquetteDecorator();
  public static final CharSequence COMPONENT = UTF8BytesString.create("moquette");
  public static final CharSequence RECEIVE = UTF8BytesString.create("moquette.receive");
  public static final CharSequence PUBLISH = UTF8BytesString.create("moquette.publish");
  public static final CharSequence DELIVER = UTF8BytesString.create("moquette.deliver");
  public static final CharSequence CALLBACK = UTF8BytesString.create("moquette.on_publish");

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"moquette"};
  }

  @Override
  protected CharSequence component() {
    return COMPONENT;
  }

  @Override
  protected CharSequence spanType() {
    return InternalSpanTypes.MESSAGE_BROKER;
  }

  @Override
  protected String service() {
    return null;
  }

  @Override
  protected String spanKind() {
    return Tags.SPAN_KIND_PRODUCER;
  }

  public ContextScope start(CharSequence operation, MqttPublishMessage message, String clientId) {
    AgentSpanContext parent = null;
    AgentSpan active = currentSpan();
    boolean detachedDelivery =
        operation == DELIVER
            && (active == null
                || (!RECEIVE.equals(active.getOperationName())
                    && !PUBLISH.equals(active.getOperationName())));
    if (detachedDelivery) {
      MqttPayload.Decoded decoded = MoquettePayload.decode(message);
      if (decoded != null) {
        parent = decoded.parent();
      }
    }
    AgentSpan span =
        parent == null
            ? startSpan("moquette", operation)
            : startSpan("moquette", operation, parent);
    return activate(span, operation, message, clientId);
  }

  public ContextScope receive(
      MqttPublishMessage message, String clientId, MqttPayload.Decoded decoded) {
    AgentSpan span = startSpan("moquette", RECEIVE, decoded == null ? null : decoded.parent());
    return activate(span, RECEIVE, message, clientId);
  }

  private ContextScope activate(
      AgentSpan span, CharSequence operation, MqttPublishMessage message, String clientId) {
    afterStart(span);
    if (operation == RECEIVE) {
      span.setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_SERVER);
    }
    onMessage(
        span,
        operation,
        message.variableHeader().topicName(),
        clientId,
        message.fixedHeader().qosLevel().value(),
        message.fixedHeader().isRetain(),
        message.fixedHeader().isDup());
    return activateSpan(span);
  }

  public ContextScope onPublish(InterceptPublishMessage message) {
    String topic = message.getTopicName();
    String clientId = message.getClientID();
    int qos = message.getQos().value();
    boolean retained = message.isRetainFlag();
    boolean duplicate = message.isDupFlag();
    AgentSpan span = startSpan("moquette", CALLBACK);
    afterStart(span);
    span.setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CONSUMER);
    span.setSpanType(InternalSpanTypes.MESSAGE_CONSUMER);
    onMessage(span, CALLBACK, topic, clientId, qos, retained, duplicate);
    return activateSpan(span);
  }

  private void onMessage(
      AgentSpan span,
      CharSequence operation,
      String topic,
      String clientId,
      int qos,
      boolean retained,
      boolean duplicate) {
    span.setResourceName(operation + " " + topic);
    span.setTag("messaging.system", "mqtt");
    span.setTag("mqtt.topic", topic);
    span.setTag("mqtt.clientid", clientId);
    span.setTag("mqtt.message.qos", qos);
    span.setTag("mqtt.message.retained", retained);
    span.setTag("mqtt.message.duplicate", duplicate);
  }

  public void finish(ContextScope scope, Throwable error) {
    if (scope != null) {
      AgentSpan span = spanFromScope(scope);
      onError(span, error);
      beforeFinish(span);
      scope.close();
      span.finish();
    }
  }
}
