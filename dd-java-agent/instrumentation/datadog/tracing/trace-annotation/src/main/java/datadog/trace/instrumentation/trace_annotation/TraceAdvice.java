package datadog.trace.instrumentation.trace_annotation;

import static datadog.trace.bootstrap.debugger.DebuggerContext.captureCodeOrigin;
import static datadog.trace.bootstrap.debugger.DebuggerContext.marker;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.setAsyncPropagationEnabled;
import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;
import static datadog.trace.instrumentation.trace_annotation.TraceDecorator.DECORATE;

import datadog.context.ContextScope;
import datadog.trace.api.Trace;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public class TraceAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static ContextScope onEnter(
      @Advice.Origin("#t") final String className,
      @Advice.Origin final Method method,
      @Advice.AllArguments final Object[] args) {
    final AgentSpan span = DECORATE.startMethodSpan(method);
    Parameter[] parameters = method.getParameters();
    StringBuilder methodName = new StringBuilder(method.getName()).append("(");
    int maxSize = 1024;
    for (int i = 0; i < parameters.length; i++) {
      String value = args[i] == null ? "null" : args[i].toString();
      span.setTag(parameters[i].getName(), value.substring(0, Math.min(maxSize, value.length())));
      methodName
          .append(parameters[i].getType().getTypeName())
          .append(" ")
          .append(parameters[i].getName());
      if (i < parameters.length - 1) {
        methodName.append(",");
      }
    }
    span.setTag("method_name", methodName.append(")").toString());
    Trace traceAnnotation = method.getAnnotation(Trace.class);
    String resourceName = null;
    if (traceAnnotation != null) {
      try {
        resourceName = traceAnnotation.resourceName();
      } catch (Throwable ignored) {
        // dd-trace-api < 0.31.0 on the application classpath.
      }
    }
    if (resourceName == null || resourceName.isEmpty()) {
      span.setResourceName(DECORATE.getMethodName(className) + "." + method.getName());
    }
    ContextScope scope = activateSpan(span);
    setAsyncPropagationEnabled(true);
    marker();
    captureCodeOrigin(method, true);
    return scope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Enter final ContextScope scope,
      @Advice.Return(typing = Assigner.Typing.DYNAMIC, readOnly = false) Object result,
      @Advice.Origin final MethodType methodType,
      @Advice.Thrown final Throwable throwable) {
    DECORATE.onError(scope, throwable);
    DECORATE.beforeFinish(scope);
    scope.close();
    // we must check against the method return signature and not the type of the returned object
    // otherwise we'll risk to have class cast at runtime
    result =
        DECORATE.wrapAsyncResultOrFinishSpan(result, methodType.returnType(), spanFromScope(scope));
  }
}
