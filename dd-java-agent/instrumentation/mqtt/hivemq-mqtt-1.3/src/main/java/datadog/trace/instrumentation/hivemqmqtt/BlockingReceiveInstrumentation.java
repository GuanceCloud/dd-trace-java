package datadog.trace.instrumentation.hivemqmqtt;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.namedOneOf;
import static datadog.trace.instrumentation.hivemqmqtt.HiveMqttDecorator.CONSUMER;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;

import com.hivemq.client.mqtt.MqttClientConfig;
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient.Mqtt3Publishes;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.InstrumentationContext;
import java.util.Optional;
import net.bytebuddy.asm.Advice;

public class BlockingReceiveInstrumentation
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {
  @Override
  public String instrumentedType() {
    return "com.hivemq.client.internal.mqtt.mqtt3.Mqtt3BlockingClientView$Mqtt3PublishesView";
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isMethod().and(namedOneOf("receive", "receiveNow")),
        getClass().getName() + "$ReceiveAdvice");
  }

  public static class ReceiveAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit(
        @Advice.This Mqtt3Publishes publishes,
        @Advice.Return(
                typing = net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC)
            Object result) {
      Object message = result instanceof Optional ? ((Optional<?>) result).orElse(null) : result;
      MqttClientConfig config =
          InstrumentationContext.get(Mqtt3Publishes.class, MqttClientConfig.class).get(publishes);
      if (config != null && message instanceof Mqtt3Publish) {
        CONSUMER.finish(CONSUMER.start(config, (Mqtt3Publish) message), null);
      }
    }
  }
}
