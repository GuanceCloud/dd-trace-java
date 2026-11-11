package datadog.trace.instrumentation.hivemqmqtt;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;

import com.hivemq.client.mqtt.mqtt3.Mqtt3RxClient;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import datadog.trace.agent.tooling.Instrumenter;
import io.reactivex.Flowable;
import net.bytebuddy.asm.Advice;

public class RxClientInstrumentation
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {
  @Override
  public String instrumentedType() {
    return "com.hivemq.client.internal.mqtt.mqtt3.Mqtt3RxClientView";
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isMethod().and(namedOneOf("publishes", "subscribePublishes", "subscribeStream")),
        getClass().getName() + "$PublishesAdvice");
  }

  public static class PublishesAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit(
        @Advice.This Mqtt3RxClient client,
        @Advice.Return(
                readOnly = false,
                typing = net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC)
            Flowable<Mqtt3Publish> publishes) {
      if (publishes != null && !(publishes instanceof TracingPublishes)) {
        publishes = new TracingPublishes(publishes, client.getConfig());
      }
    }
  }
}
