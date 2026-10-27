package datadog.trace.instrumentation.disruptor;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.*;
import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;
import static datadog.trace.instrumentation.disruptor.DisruptorDecorator.DECORATE;

import datadog.context.ContextScope;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import net.bytebuddy.asm.Advice;

public class RingBufferPublishAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static ContextScope beginRequest(@Advice.Argument(0) Long sequence) {

    ContextScope scope = DECORATE.start();
    if (scope == null) {
      // scope = activeSpan();
      AgentSpan agentSpan = activeSpan();
      scope = activateSpan(agentSpan);
    }
    InstrumentationContext.get(Long.class, AgentSpan.class).put(sequence, spanFromScope(scope));
    return scope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Enter final ContextScope scope, @Advice.Thrown final Throwable throwable) {
    if (scope == null) {
      return;
    }
    DECORATE.onError(spanFromScope(scope), throwable);
    DECORATE.beforeFinish(spanFromScope(scope));

    scope.close();
    spanFromScope(scope).finish();
  }
}
