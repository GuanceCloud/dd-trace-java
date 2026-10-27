package datadog.trace.instrumentation.dubbo_3_2;

import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;
import static datadog.trace.instrumentation.dubbo_3_2.DubboDecorator.DECORATE;

import datadog.context.ContextScope;
import net.bytebuddy.asm.Advice;
import org.apache.dubbo.rpc.RpcInvocation;

public class DubboServerCallAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static ContextScope onEnter(@Advice.FieldValue("invocation") RpcInvocation invocation) {
    return DECORATE.serverCall(invocation);
  }

  //
  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Enter final ContextScope scope,
      @Advice.FieldValue("invocation") RpcInvocation invocation,
      @Advice.Thrown final Throwable throwable) {
    if (scope == null) {
      return;
    }
    DECORATE.onError(spanFromScope(scope), throwable);
    DECORATE.beforeFinish(spanFromScope(scope));
    scope.close();
    spanFromScope(scope).finish();
  }
}
