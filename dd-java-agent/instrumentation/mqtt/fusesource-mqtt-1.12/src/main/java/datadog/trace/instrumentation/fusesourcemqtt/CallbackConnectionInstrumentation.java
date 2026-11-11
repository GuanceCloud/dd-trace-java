package datadog.trace.instrumentation.fusesourcemqtt;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;
import static datadog.trace.instrumentation.fusesourcemqtt.FuseSourceMqttDecorator.CONSUMER;
import static datadog.trace.instrumentation.fusesourcemqtt.FuseSourceMqttDecorator.PRODUCER;
import static datadog.trace.instrumentation.mqtt.MqttClientDecorator.CONSUME;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import datadog.context.ContextScope;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import net.bytebuddy.asm.Advice;
import org.fusesource.hawtbuf.UTF8Buffer;
import org.fusesource.mqtt.client.Callback;
import org.fusesource.mqtt.client.CallbackConnection;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.Promise;
import org.fusesource.mqtt.client.QoS;
import org.fusesource.mqtt.codec.PUBLISH;

public class CallbackConnectionInstrumentation
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {
  @Override
  public String instrumentedType() {
    return "org.fusesource.mqtt.client.CallbackConnection";
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isConstructor()
            .and(takesArguments(1))
            .and(takesArgument(0, named("org.fusesource.mqtt.client.MQTT"))),
        getClass().getName() + "$ConstructAdvice");
    transformer.applyAdvice(
        isMethod()
            .and(named("publish"))
            .and(takesArguments(5))
            .and(takesArgument(0, named("org.fusesource.hawtbuf.UTF8Buffer"))),
        getClass().getName() + "$PublishAdvice");
    transformer.applyAdvice(
        isMethod()
            .and(named("toReceiver"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("org.fusesource.mqtt.codec.PUBLISH"))),
        getClass().getName() + "$ConsumeAdvice");
    transformer.applyAdvice(
        isMethod()
            .and(named("handleFatalFailure"))
            .and(takesArguments(1))
            .and(takesArgument(0, Throwable.class)),
        getClass().getName() + "$FailureAdvice");
  }

  public static class ConstructAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit(
        @Advice.This CallbackConnection connection, @Advice.Argument(0) MQTT mqtt) {
      if (mqtt != null) {
        InstrumentationContext.get(CallbackConnection.class, MQTT.class).put(connection, mqtt);
      }
    }
  }

  public static class PublishAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static PublishState enter(
        @Advice.FieldValue("mqtt") MQTT mqtt,
        @Advice.Argument(0) UTF8Buffer topic,
        @Advice.Argument(2) QoS qos,
        @Advice.Argument(3) boolean retained,
        @Advice.Argument(value = 4, readOnly = false) Callback callback,
        @Advice.Local("scope") ContextScope scope) {
      if (topic == null || qos == null || callback instanceof PublishCallback) {
        return null;
      }
      if (callback instanceof Promise
          && InstrumentationContext.get(Promise.class, PublishState.class).get((Promise) callback)
              != null) {
        return null;
      }
      PublishState state = new PublishState(PRODUCER.start(mqtt, topic.toString(), qos, retained));
      callback = new PublishCallback(callback, state);
      scope = state.activate();
      return state;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(
        @Advice.Enter PublishState state,
        @Advice.Local("scope") ContextScope scope,
        @Advice.Thrown Throwable error) {
      if (scope != null) {
        scope.close();
      }
      if (state != null && error != null) {
        state.finish(error);
      }
    }
  }

  public static class ConsumeAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ContextScope enter(
        @Advice.FieldValue("mqtt") MQTT mqtt, @Advice.Argument(0) PUBLISH message) {
      return activateSpan(CONSUMER.receive(mqtt, message));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter ContextScope scope, @Advice.Thrown Throwable error) {
      if (scope != null) {
        AgentSpan span = spanFromScope(scope);
        CONSUMER.onError(span, error);
        CONSUMER.beforeFinish(span);
        scope.close();
        span.finish();
      }
    }
  }

  public static class FailureAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(@Advice.Argument(0) Throwable error) {
      AgentSpan span = activeSpan();
      if (span != null && CONSUME.equals(span.getOperationName())) {
        CONSUMER.onError(span, error);
      }
    }
  }
}
