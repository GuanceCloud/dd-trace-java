package datadog.trace.instrumentation.javax.script;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;
import static datadog.trace.instrumentation.javax.script.JavaxScriptDecorator.DECORATE;

import datadog.context.ContextScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import net.bytebuddy.asm.Advice;

public class JavaxScriptInvokeAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static ContextScope methodEnter(@Advice.Argument(0) String name) {
    final AgentSpan span = startSpan(JavaxScriptDecorator.OPERATION_NAME + "/invoke");
    span.setTag("invoke_function", name);
    final ContextScope scope = activateSpan(span);

    DECORATE.afterStart(span);
    return scope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void methodExit(
      @Advice.Enter final ContextScope scope, @Advice.Thrown final Throwable throwable) {
    if (scope == null) {
      return;
    }

    AgentSpan span = spanFromScope(scope);
    if (null != throwable) {
      DECORATE.onError(span, throwable);
    }
    DECORATE.beforeFinish(span);
    scope.close();
    span.finish();
  }
}
