package datadog.trace.instrumentation.moquette;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import datadog.trace.agent.tooling.Instrumenter;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import net.bytebuddy.asm.Advice;

public class InterceptMessageInstrumentation
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {
  @Override
  public String instrumentedType() {
    return "io.moquette.interception.messages.InterceptPublishMessage";
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isConstructor().and(takesArguments(3)), getClass().getName() + "$PayloadAdvice");
  }

  public static class PayloadAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(
        @Advice.Argument(value = 0, readOnly = false) MqttPublishMessage message) {
      if (message != null) {
        message = MoquettePayload.forCallback(message);
      }
    }
  }
}
