package datadog.trace.instrumentation.dubbo_3_2;

import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;
import static datadog.trace.instrumentation.dubbo_3_2.DubboDecorator.DECORATE;

import datadog.context.ContextScope;
import net.bytebuddy.asm.Advice;
import org.apache.dubbo.rpc.protocol.tri.call.ClientCall;
import org.apache.dubbo.rpc.protocol.tri.call.TripleClientCall;

public class DubboObserverToClientCallAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static ContextScope onEnter(@Advice.FieldValue("call") ClientCall call) {
    TripleClientCall tripleClientCall = (TripleClientCall) call;
    return DECORATE.clientCall(tripleClientCall);
  }

  //
  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Enter final ContextScope scope, @Advice.Thrown final Throwable throwable) {
    if (scope == null) {
      return;
    }
    DECORATE.beforeFinish(spanFromScope(scope));
    scope.close();
    spanFromScope(scope).finish();
  }
}
