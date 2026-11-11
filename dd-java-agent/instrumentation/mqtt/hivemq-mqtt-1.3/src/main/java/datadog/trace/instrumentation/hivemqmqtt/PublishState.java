package datadog.trace.instrumentation.hivemqmqtt;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.hivemqmqtt.HiveMqttDecorator.PRODUCER;

import datadog.context.ContextScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.concurrent.atomic.AtomicBoolean;

/** One span per subscribed publish, completed by success, failure, or cancellation. */
public final class PublishState {
  public final AgentSpan span;
  private final AtomicBoolean finished = new AtomicBoolean();

  public PublishState(AgentSpan span) {
    this.span = span;
  }

  public ContextScope activate() {
    return activateSpan(span);
  }

  public void finish(Throwable error) {
    if (finished.compareAndSet(false, true)) {
      ContextScope scope = activateSpan(span);
      PRODUCER.onError(span, error);
      PRODUCER.beforeFinish(span);
      scope.close();
      span.finish();
    }
  }
}
