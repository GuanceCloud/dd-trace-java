package datadog.trace.instrumentation.alibaba.dubbo;

import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;
import static datadog.trace.instrumentation.alibaba.dubbo.DubboDecorator.DECORATE;

import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import datadog.context.ContextScope;
import net.bytebuddy.asm.Advice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestAdvice {
  public static final Logger logger = LoggerFactory.getLogger(RequestAdvice.class);

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static ContextScope beginRequest(
      @Advice.This Filter filter,
      @Advice.Argument(0) final Invoker invoker,
      @Advice.Argument(1) final Invocation invocation) {
    // logger.info("dubboFilterName:"+filter.getClass().getName());

    //    final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(RpcContext.class);
    //    if (callDepth > 0) {
    //      return null;
    //    }
    ContextScope agentScope = DECORATE.buildSpan(invoker, invocation);
    return agentScope;
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
    //    CallDepthThreadLocalMap.reset(RpcContext.class);
  }
}
