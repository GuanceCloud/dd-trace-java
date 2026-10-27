package datadog.trace.instrumentation.rhino;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;
import static datadog.trace.instrumentation.rhino.RhinoDecorator.DECORATOR;

import datadog.context.ContextScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import net.bytebuddy.asm.Advice;

public class RhinoAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static ContextScope execute(@Advice.AllArguments final Object[] args) {
    String script = (String) args[1];
    String scriptName = (String) args[2];

    AgentSpan span = DECORATOR.createSpan(scriptName, script);
    ContextScope agentScope = activateSpan(span);
    return agentScope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void exit(
      @Advice.Enter final ContextScope scope, @Advice.Thrown final Throwable throwable) {
    if (scope == null) {
      return;
    }
    DECORATOR.onError(spanFromScope(scope), throwable);
    DECORATOR.beforeFinish(spanFromScope(scope));
    scope.close();
    spanFromScope(scope).finish();
  }
}
