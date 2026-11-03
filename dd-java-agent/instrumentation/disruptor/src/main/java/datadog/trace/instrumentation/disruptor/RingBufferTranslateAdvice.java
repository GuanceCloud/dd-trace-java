package datadog.trace.instrumentation.disruptor;

import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;
import static datadog.trace.instrumentation.disruptor.DisruptorDecorator.DECORATE;

import com.lmax.disruptor.RingBuffer;
import datadog.context.ContextScope;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.WeakMap;
import net.bytebuddy.asm.Advice;

public class RingBufferTranslateAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static ContextScope beginRequest(
      @Advice.This RingBuffer<?> ring, @Advice.Argument(1) long sequence) {
    return DECORATE.start(
        ring, sequence, sequence, InstrumentationContext.get(Class.class, WeakMap.class));
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
