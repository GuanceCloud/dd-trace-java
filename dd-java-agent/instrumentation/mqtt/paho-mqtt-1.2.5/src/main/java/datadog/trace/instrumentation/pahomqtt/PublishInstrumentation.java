package datadog.trace.instrumentation.pahomqtt;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import datadog.context.ContextScope;
import datadog.trace.agent.tooling.Instrumenter;
import net.bytebuddy.asm.Advice;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class PublishInstrumentation
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {
  @Override
  public String instrumentedType() {
    return "org.eclipse.paho.client.mqttv3.MqttAsyncClient";
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isMethod()
            .and(named("publish"))
            .and(takesArguments(4))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, named("org.eclipse.paho.client.mqttv3.MqttMessage"))),
        getClass().getName() + "$PublishAdvice");
  }

  public static class PublishAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static PublishState enter(
        @Advice.This MqttAsyncClient client,
        @Advice.Argument(0) String topic,
        @Advice.Argument(value = 1, readOnly = false) MqttMessage message,
        @Advice.Local("scope") ContextScope scope) {
      if (message == null) {
        return null;
      }
      PublishState state = new PublishState(client, topic, message);
      scope = state.activatePublish();
      message = state.outgoing;
      return state;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(
        @Advice.Enter PublishState state,
        @Advice.Local("scope") ContextScope scope,
        @Advice.Thrown Throwable error) {
      if (state != null) {
        state.restoreMessageId();
        scope.close();
        if (error != null) {
          state.finish(error);
        }
      }
    }
  }
}
