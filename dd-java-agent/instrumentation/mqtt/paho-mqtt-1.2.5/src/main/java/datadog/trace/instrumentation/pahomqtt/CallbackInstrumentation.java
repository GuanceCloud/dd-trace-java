package datadog.trace.instrumentation.pahomqtt;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;
import static datadog.trace.instrumentation.pahomqtt.PahoMqttDecorator.CONSUMER;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import datadog.context.ContextScope;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.instrumentation.mqtt.payload.MqttPayload;
import net.bytebuddy.asm.Advice;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttToken;
import org.eclipse.paho.client.mqttv3.internal.ClientComms;

public class CallbackInstrumentation
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {
  @Override
  public String instrumentedType() {
    return "org.eclipse.paho.client.mqttv3.internal.CommsCallback";
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isMethod().and(named("deliverMessage")).and(takesArguments(3)),
        getClass().getName() + "$ConsumeAdvice");
    transformer.applyAdvice(
        isMethod().and(named("handleActionComplete")).and(takesArguments(1)),
        getClass().getName() + "$CompleteAdvice");
  }

  public static class ConsumeAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ContextScope enter(
        @Advice.Argument(0) String topic,
        @Advice.Argument(value = 2, readOnly = false) MqttMessage message,
        @Advice.FieldValue("clientComms") ClientComms comms) {
      MqttPayload.Decoded decoded = TracingMqttMessage.decode(message);
      ContextScope scope =
          activateSpan(
              CONSUMER.start(
                  topic,
                  message,
                  comms.getClient().getClientId(),
                  comms.getClient().getServerURI(),
                  decoded));
      message = TracingMqttMessage.unwrap(message, decoded);
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter ContextScope scope, @Advice.Thrown Throwable error) {
      if (scope != null) {
        CONSUMER.onError(spanFromScope(scope), error);
        CONSUMER.beforeFinish(spanFromScope(scope));
        scope.close();
        spanFromScope(scope).finish();
      }
    }
  }

  public static class CompleteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static PublishState enter(
        @Advice.Argument(0) MqttToken token, @Advice.Local("scope") ContextScope scope) {
      if (token == null) {
        return null;
      }
      PublishState state =
          InstrumentationContext.get(MqttToken.class, PublishState.class).get(token);
      if (state != null && !state.isFinished()) {
        state.restoreMessageId();
        scope = activateSpan(state.span);
      }
      return state;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(
        @Advice.Argument(0) MqttToken token,
        @Advice.Enter PublishState state,
        @Advice.Local("scope") ContextScope scope,
        @Advice.Thrown Throwable error) {
      if (state != null) {
        InstrumentationContext.get(MqttToken.class, PublishState.class).remove(token);
        if (scope != null) {
          scope.close();
        }
        state.finish(error == null ? token.getException() : error);
      }
    }
  }
}
