package datadog.trace.instrumentation.fusesourcemqtt;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import datadog.context.ContextScope;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.InstrumentationContext;
import net.bytebuddy.asm.Advice;
import org.fusesource.mqtt.client.Promise;

public class PromiseInstrumentation
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {
  @Override
  public String instrumentedType() {
    return "org.fusesource.mqtt.client.Promise";
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(isConstructor(), getClass().getName() + "$ConstructAdvice");
    transformer.applyAdvice(
        isMethod().and(namedOneOf("onSuccess", "onFailure")).and(takesArguments(1)),
        getClass().getName() + "$CompleteAdvice");
  }

  public static class ConstructAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit(@Advice.This Promise promise) {
      PublishState state = PublishState.current();
      if (state != null) {
        InstrumentationContext.get(Promise.class, PublishState.class).put(promise, state);
      }
    }
  }

  public static class CompleteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static PublishState enter(
        @Advice.This Promise promise, @Advice.Local("scope") ContextScope scope) {
      PublishState state =
          InstrumentationContext.get(Promise.class, PublishState.class).remove(promise);
      if (state != null) {
        scope = state.activate();
      }
      return state;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(
        @Advice.Enter PublishState state,
        @Advice.Argument(0) Object value,
        @Advice.Local("scope") ContextScope scope,
        @Advice.Thrown Throwable error) {
      if (state != null) {
        Throwable failure =
            error != null ? error : value instanceof Throwable ? (Throwable) value : null;
        FuseSourceMqttDecorator.PRODUCER.onError(state.span, failure);
        scope.close();
        state.finish(failure);
      }
    }
  }
}
