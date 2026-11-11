package datadog.trace.instrumentation.hivemqmqtt;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;

import com.hivemq.client.mqtt.MqttClientConfig;
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient.Mqtt3Publishes;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.InstrumentationContext;
import net.bytebuddy.asm.Advice;

public class BlockingClientInstrumentation
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {
  @Override
  public String instrumentedType() {
    return "com.hivemq.client.internal.mqtt.mqtt3.Mqtt3BlockingClientView";
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isMethod().and(named("publishes")), getClass().getName() + "$PublishesAdvice");
  }

  public static class PublishesAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit(
        @Advice.This Mqtt3BlockingClient client, @Advice.Return Mqtt3Publishes publishes) {
      if (publishes != null) {
        InstrumentationContext.get(Mqtt3Publishes.class, MqttClientConfig.class)
            .put(publishes, client.getConfig());
      }
    }
  }
}
