package datadog.trace.instrumentation.pahomqtt;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.InstrumentationContext;
import net.bytebuddy.asm.Advice;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttToken;

public class DeliveryTokenInstrumentation
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {
  @Override
  public String instrumentedType() {
    return "org.eclipse.paho.client.mqttv3.MqttDeliveryToken";
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isMethod().and(named("getMessage")).and(takesArguments(0)),
        getClass().getName() + "$MessageAdvice");
  }

  public static class MessageAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit(
        @Advice.This MqttToken token, @Advice.Return(readOnly = false) MqttMessage message) {
      if (message != null) {
        PublishState state =
            InstrumentationContext.get(MqttToken.class, PublishState.class).get(token);
        message = TracingMqttMessage.forToken(message, state);
      }
    }
  }
}
