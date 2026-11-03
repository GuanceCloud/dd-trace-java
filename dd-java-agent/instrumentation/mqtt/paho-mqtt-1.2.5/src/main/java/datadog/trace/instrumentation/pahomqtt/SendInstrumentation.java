package datadog.trace.instrumentation.pahomqtt;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.InstrumentationContext;
import net.bytebuddy.asm.Advice;
import org.eclipse.paho.client.mqttv3.MqttToken;
import org.eclipse.paho.client.mqttv3.internal.wire.MqttPublish;
import org.eclipse.paho.client.mqttv3.internal.wire.MqttWireMessage;

public class SendInstrumentation
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {
  @Override
  public String instrumentedType() {
    return "org.eclipse.paho.client.mqttv3.internal.ClientComms";
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isMethod().and(named("sendNoWait")).and(takesArguments(2)),
        getClass().getName() + "$SendAdvice");
  }

  public static class SendAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(
        @Advice.Argument(0) MqttWireMessage message, @Advice.Argument(1) MqttToken token) {
      if (message instanceof MqttPublish && token != null) {
        PublishState state = PublishState.current();
        if (state != null) {
          InstrumentationContext.get(MqttToken.class, PublishState.class).put(token, state);
        }
      }
    }
  }
}
