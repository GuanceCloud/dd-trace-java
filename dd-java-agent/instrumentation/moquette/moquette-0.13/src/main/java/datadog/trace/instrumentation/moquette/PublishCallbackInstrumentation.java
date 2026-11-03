package datadog.trace.instrumentation.moquette;

import static datadog.trace.agent.tooling.bytebuddy.matcher.HierarchyMatchers.implementsInterface;
import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.instrumentation.moquette.MoquetteDecorator.DECORATE;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import datadog.context.ContextScope;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import io.moquette.interception.InterceptHandler;
import io.moquette.interception.messages.InterceptPublishMessage;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class PublishCallbackInstrumentation
    implements Instrumenter.ForTypeHierarchy, Instrumenter.HasMethodAdvice {
  @Override
  public String hierarchyMarkerType() {
    return "io.moquette.interception.InterceptHandler";
  }

  @Override
  public ElementMatcher<TypeDescription> hierarchyMatcher() {
    return implementsInterface(named(hierarchyMarkerType()));
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isMethod()
            .and(named("onPublish"))
            .and(takesArguments(1))
            .and(
                takesArgument(
                    0, named("io.moquette.interception.messages.InterceptPublishMessage"))),
        getClass().getName() + "$CallbackAdvice");
  }

  public static class CallbackAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ContextScope enter(@Advice.Argument(0) InterceptPublishMessage message) {
      if (message == null || CallDepthThreadLocalMap.getCallDepth(InterceptHandler.class) > 0) {
        return null;
      }
      ContextScope scope = DECORATE.onPublish(message);
      CallDepthThreadLocalMap.incrementCallDepth(InterceptHandler.class);
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter ContextScope scope, @Advice.Thrown Throwable error) {
      if (scope != null) {
        CallDepthThreadLocalMap.reset(InterceptHandler.class);
        DECORATE.finish(scope, error);
      }
    }
  }
}
