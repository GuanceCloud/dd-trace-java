package datadog.trace.instrumentation.dubbo_2_7x;

import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;
import static datadog.trace.instrumentation.dubbo_2_7x.DubboDecorator.DECORATE;

import datadog.context.ContextScope;
import net.bytebuddy.asm.Advice;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.proxy.AbstractProxyInvoker;

public class DubboProviderAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static ContextScope beginRequest(
      @Advice.This AbstractProxyInvoker invoker, @Advice.Argument(0) final Invocation invocation) {
    ContextScope scope = DECORATE.buildSpan(invoker, invocation);
    return scope;
    //    return activeScope();
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Enter final ContextScope scope,
      @Advice.Return Result result,
      @Advice.Thrown final Throwable throwable) {
    if (scope == null) {
      return;
    }
    DECORATE.buildResult(scope, result);
    DECORATE.onError(result, spanFromScope(scope), throwable);
    DECORATE.beforeFinish(spanFromScope(scope));

    scope.close();
    spanFromScope(scope).finish();
  }
}
