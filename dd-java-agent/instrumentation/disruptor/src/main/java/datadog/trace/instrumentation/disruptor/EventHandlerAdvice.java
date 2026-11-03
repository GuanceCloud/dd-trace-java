package datadog.trace.instrumentation.disruptor;

import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;
import static datadog.trace.instrumentation.disruptor.DisruptorDecorator.DECORATE;

import datadog.context.ContextScope;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.WeakMap;
import datadog.trace.bootstrap.instrumentation.api.AgentSpanContext;
import net.bytebuddy.asm.Advice;

public class EventHandlerAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static ContextScope beginRequest(@Advice.Argument(0) Object event) {
    if (event == null) return null;
    AgentSpanContext parent =
        DECORATE.contexts(InstrumentationContext.get(Class.class, WeakMap.class)).get(event);
    return parent == null ? null : DECORATE.startBySpan(parent);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(@Advice.Enter ContextScope scope, @Advice.Thrown Throwable error) {
    if (scope == null) return;
    try {
      DECORATE.onError(spanFromScope(scope), error);
      DECORATE.beforeFinish(spanFromScope(scope));
    } finally {
      scope.close();
      spanFromScope(scope).finish();
    }
  }
}
