package datadog.trace.instrumentation.fusesourcemqtt;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.fusesourcemqtt.FuseSourceMqttDecorator.PRODUCER;

import datadog.context.Context;
import datadog.context.ContextKey;
import datadog.context.ContextScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns completion without occupying FuseSource Promise's single application callback slot. */
public final class PublishState {
  private static final ContextKey<PublishState> KEY = ContextKey.named("fusesource-mqtt-publish");
  public final AgentSpan span;
  private final AtomicBoolean finished = new AtomicBoolean();

  public PublishState(AgentSpan span) {
    this.span = span;
  }

  public static PublishState current() {
    return Context.current().get(KEY);
  }

  public ContextScope capture() {
    return Context.current().with(span).with(KEY, this).attach();
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
