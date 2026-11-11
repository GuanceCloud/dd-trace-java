package datadog.trace.instrumentation.fusesourcemqtt;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.instrumentation.fusesourcemqtt.FuseSourceMqttDecorator.PRODUCER;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import datadog.context.ContextScope;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.InstrumentationContext;
import net.bytebuddy.asm.Advice;
import org.fusesource.hawtbuf.UTF8Buffer;
import org.fusesource.mqtt.client.CallbackConnection;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.QoS;

public class FutureConnectionInstrumentation
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {
  @Override
  public String instrumentedType() {
    return "org.fusesource.mqtt.client.FutureConnection";
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isMethod()
            .and(named("publish"))
            .and(takesArguments(4))
            .and(takesArgument(0, named("org.fusesource.hawtbuf.UTF8Buffer"))),
        getClass().getName() + "$PublishAdvice");
  }

  public static class PublishAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static PublishState enter(
        @Advice.FieldValue("next") CallbackConnection connection,
        @Advice.Argument(0) UTF8Buffer topic,
        @Advice.Argument(2) QoS qos,
        @Advice.Argument(3) boolean retained,
        @Advice.Local("scope") ContextScope scope) {
      if (topic == null || qos == null) {
        return null;
      }
      MQTT mqtt = InstrumentationContext.get(CallbackConnection.class, MQTT.class).get(connection);
      PublishState state = new PublishState(PRODUCER.start(mqtt, topic.toString(), qos, retained));
      scope = state.capture();
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
