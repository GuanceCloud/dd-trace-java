package datadog.trace.instrumentation.moquette;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;
import static datadog.trace.instrumentation.moquette.MoquetteDecorator.DECORATE;
import static datadog.trace.instrumentation.moquette.MoquetteDecorator.PUBLISH;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import datadog.context.ContextScope;
import datadog.trace.agent.tooling.Instrumenter;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import net.bytebuddy.asm.Advice;

public class PostOfficeInstrumentation
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {
  @Override
  public String instrumentedType() {
    return "io.moquette.broker.PostOffice";
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isMethod().and(named("internalPublish")).and(takesArguments(1)),
        getClass().getName() + "$PublishAdvice");
  }

  public static class PublishAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ContextScope enter(
        @Advice.Argument(value = 0, readOnly = false) MqttPublishMessage message,
        @Advice.Local("propagated") MqttPublishMessage propagated) {
      ContextScope scope = DECORATE.start(PUBLISH, message, null);
      propagated = MoquettePayload.reframe(spanFromScope(scope), message, true, null);
      if (propagated != null) {
        message = propagated;
      }
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(
        @Advice.Enter ContextScope scope,
        @Advice.Thrown Throwable error,
        @Advice.Local("propagated") MqttPublishMessage propagated) {
      if (propagated != null) {
        propagated.release();
      }
      DECORATE.finish(scope, error);
    }
  }
}
