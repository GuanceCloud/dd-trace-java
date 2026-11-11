package datadog.trace.instrumentation.hivemqmqtt;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.namedOneOf;
import static datadog.trace.instrumentation.hivemqmqtt.HiveMqttDecorator.PRODUCER;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.hivemq.client.mqtt.MqttClientConfig;
import com.hivemq.client.mqtt.MqttVersion;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import datadog.context.ContextScope;
import datadog.trace.agent.tooling.Instrumenter;
import io.reactivex.SingleObserver;
import net.bytebuddy.asm.Advice;
import org.reactivestreams.Subscriber;

public class PublishInstrumentation
    implements Instrumenter.ForKnownTypes, Instrumenter.HasMethodAdvice {
  @Override
  public String[] knownMatchingTypes() {
    return new String[] {
      "com.hivemq.client.internal.mqtt.handler.publish.outgoing.MqttAckSingle",
      "com.hivemq.client.internal.mqtt.handler.publish.outgoing.MqttAckSingleFlowable"
    };
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isMethod()
            .and(named("subscribeActual"))
            .and(takesArguments(1))
            .and(
                takesArgument(
                    0,
                    namedOneOf("io.reactivex.SingleObserver", "org.reactivestreams.Subscriber"))),
        getClass().getName() + "$PublishAdvice");
  }

  public static class PublishAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static PublishState enter(
        @Advice.FieldValue("clientConfig") MqttClientConfig config,
        @Advice.FieldValue("publish") Mqtt5Publish publish,
        @Advice.Argument(
                value = 0,
                readOnly = false,
                typing = net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC)
            Object observer,
        @Advice.Local("scope") ContextScope scope) {
      if (config.getMqttVersion() != MqttVersion.MQTT_3_1_1) {
        return null;
      }
      PublishState state = new PublishState(PRODUCER.start(config, publish));
      if (observer instanceof SingleObserver) {
        observer = new PublishObserver((SingleObserver<Object>) observer, state);
      } else {
        observer = new PublishSubscriber((Subscriber<Object>) observer, state);
      }
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
}
