package datadog.trace.instrumentation.disruptor;

import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;
import static datadog.trace.instrumentation.disruptor.DisruptorDecorator.DECORATE;

import com.lmax.disruptor.RingBuffer;
import datadog.context.ContextScope;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.WeakMap;
import net.bytebuddy.asm.Advice;

public class RingBufferTranslateBatchAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static ContextScope beginRequest(
      @Advice.This RingBuffer<?> ring, @Advice.AllArguments Object[] arguments) {
    // Across translator overloads, batchSize is the last int before the final sequence.
    int batchSize = 0;
    for (Object argument : arguments) {
      if (argument instanceof Integer) {
        batchSize = (Integer) argument;
      } else if (argument instanceof Long) {
        long high = (Long) argument;
        return DECORATE.start(
            ring,
            high - batchSize + 1,
            high,
            InstrumentationContext.get(Class.class, WeakMap.class));
      }
    }
    return null;
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
