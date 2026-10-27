package datadog.trace.instrumentation.pulsar;

import static datadog.context.propagation.Propagators.defaultPropagator;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;
import static datadog.trace.instrumentation.pulsar.MessageTextMapSetter.SETTER;

import datadog.context.ContextScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;
import datadog.trace.bootstrap.instrumentation.decorator.BaseDecorator;

public class ProducerDecorator extends BaseDecorator {
  public static final CharSequence ROCKETMQ_NAME = UTF8BytesString.create("pulsar");

  private static final String MESSAGING_SYSTEM = "messaging.system";
  private static final String MESSAGING_PAYLOAD = "messaging.payload_size_bytes";

  public ProducerDecorator() {}

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"pulsar"};
  }

  @Override
  protected CharSequence spanType() {
    return ROCKETMQ_NAME;
  }

  @Override
  protected CharSequence component() {
    return null;
  }

  public static ContextScope start(PulsarRequest request) {
    UTF8BytesString spanName = UTF8BytesString.create(request.getDestination() + " send");
    final AgentSpan span = startSpan("pulsar", spanName);
    span.setServiceName("pulsar");
    span.setResourceName(spanName);
    span.setTag("topic", request.getDestination());
    span.setTag("broker_url", request.getUrlData().getHost());
    span.setTag("broker_port", request.getUrlData().getPort());
    span.setTag(MESSAGING_PAYLOAD, request.getMessage().getData().length);
    span.setTag("messaging.id", request.getMessage().getMessageId());
    // afterStart(span);
    span.setSpanType("queue");
    defaultPropagator().inject(span, request, SETTER);
    return activateSpan(span);
  }

  public void end(ContextScope scope, PulsarRequest request, Exception e) {
    if (e != null) {
      spanFromScope(scope).setError(true);
      spanFromScope(scope).setErrorMessage(e.getMessage());
    }
    beforeFinish(scope);
    spanFromScope(scope).finish();
    scope.close();
  }
}
