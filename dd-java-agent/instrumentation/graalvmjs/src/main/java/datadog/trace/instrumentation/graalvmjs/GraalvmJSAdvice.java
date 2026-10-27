// package datadog.trace.instrumentation.graalvmjs;

//
// import datadog.context.ContextScope;
// import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
// import net.bytebuddy.asm.Advice;
// import org.graalvm.polyglot.Source;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
//
// import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
// import static datadog.trace.instrumentation.graalvmjs.GraalvmjsDecorator.DECORATOR;
//
// public class GraalvmJSAdvice {
//  public static final Logger logger = LoggerFactory.getLogger(GraalvmJSAdvice.class);
//
//  @Advice.OnMethodEnter(suppress = Throwable.class)
//  public static ContextScope beginRequest( @Advice.Argument(0) final Source source) {
//
//    AgentSpan span = DECORATOR.createSpan(source);
//    ContextScope agentScope = activateSpan(span);
//    return agentScope;
//  }
//
//  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
//  public static void stopSpan(
//      @Advice.Enter final ContextScope scope, @Advice.Thrown final Throwable throwable) {
//    if (scope == null) {
//      return;
//    }
//    DECORATOR.onError(spanFromScope(scope), throwable);
//    DECORATOR.beforeFinish(spanFromScope(scope));
//
//    scope.close();
//    spanFromScope(scope).finish();
//  }
// }
