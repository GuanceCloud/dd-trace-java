package datadog.trace.instrumentation.hivemqmqtt;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.function.Consumer;
import net.bytebuddy.asm.Advice;

public class AsyncClientInstrumentation
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {
  @Override
  public String instrumentedType() {
    return "com.hivemq.client.internal.mqtt.mqtt3.Mqtt3AsyncClientView";
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isMethod()
            .and(namedOneOf("subscribe", "publishes"))
            .and(takesArgument(1, namedOneOf("java.util.function.Consumer"))),
        getClass().getName() + "$CallbackAdvice");
  }

  public static class CallbackAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(
        @Advice.This Mqtt3AsyncClient client,
        @Advice.Argument(value = 1, readOnly = false) Consumer<Mqtt3Publish> callback) {
      if (callback != null && !(callback instanceof ConsumeCallback)) {
        callback = new ConsumeCallback(callback, client.getConfig());
      }
    }
  }
}
