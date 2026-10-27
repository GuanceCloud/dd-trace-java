package datadog.trace.instrumentation.lettuce5;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;
import static datadog.trace.instrumentation.lettuce5.LettuceClientDecorator.DECORATE;
import static datadog.trace.instrumentation.lettuce5.LettuceInstrumentationUtil.expectsResponse;

import datadog.context.ContextScope;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import io.lettuce.core.AbstractRedisAsyncCommands;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.protocol.AsyncCommand;
import io.lettuce.core.protocol.RedisCommand;
import net.bytebuddy.asm.Advice;

public class LettuceAsyncCommandsAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static ContextScope onEnter(
      @Advice.Argument(0) final RedisCommand command,
      @Advice.This final AbstractRedisAsyncCommands thiz) {
    final AgentSpan span =
        startSpan(
            LettuceClientDecorator.REDIS_CLIENT.toString(), LettuceClientDecorator.OPERATION_NAME);
    DECORATE.afterStart(span);
    DECORATE.onCommand(span, command);

    return activateSpan(span);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) final RedisCommand command,
      @Advice.This final AbstractRedisAsyncCommands thiz,
      @Advice.Enter final ContextScope scope,
      @Advice.Thrown final Throwable throwable,
      @Advice.Return AsyncCommand<?, ?, ?> asyncCommand) {
    final AgentSpan span = spanFromScope(scope);
    ContextStore<StatefulConnection, LettuceConnectionInfo> store =
        InstrumentationContext.get(StatefulConnection.class, LettuceConnectionInfo.class);
    LettuceConnectionInfo info = store.get(thiz.getConnection());
    if (info != null) {
      DECORATE.onConnection(span, info);
    }

    if (throwable != null) {
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      scope.close();
      span.finish();
      return;
    }

    // close spans on error or normal completion
    if (expectsResponse(command)) {
      asyncCommand.whenComplete(new LettuceAsyncBiConsumer<>(span));
    } else {
      DECORATE.beforeFinish(span);
      span.finish();
    }
    scope.close();
    // span may be finished by LettuceAsyncBiConsumer
  }
}
