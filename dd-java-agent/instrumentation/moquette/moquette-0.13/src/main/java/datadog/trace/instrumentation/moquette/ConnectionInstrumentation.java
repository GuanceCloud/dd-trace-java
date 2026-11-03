package datadog.trace.instrumentation.moquette;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;
import static datadog.trace.instrumentation.moquette.MoquetteDecorator.DECORATE;
import static datadog.trace.instrumentation.moquette.MoquetteDecorator.DELIVER;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import datadog.context.ContextScope;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.instrumentation.mqtt.payload.MqttPayload;
import io.moquette.broker.NettyUtils;
import io.netty.channel.Channel;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import net.bytebuddy.asm.Advice;

public class ConnectionInstrumentation
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {
  @Override
  public String instrumentedType() {
    return "io.moquette.broker.MQTTConnection";
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isMethod().and(named("processPublish")).and(takesArguments(1)).and(returns(void.class)),
        getClass().getName() + "$ReceiveAdvice");
    transformer.applyAdvice(
        isMethod().and(named("sendIfWritableElseDrop")).and(takesArguments(1)),
        getClass().getName() + "$DeliverAdvice");
  }

  public static class ReceiveAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ContextScope enter(
        @Advice.Argument(value = 0, readOnly = false) MqttPublishMessage message,
        @Advice.FieldValue("channel") Channel channel,
        @Advice.Local("propagated") MqttPublishMessage propagated) {
      MqttPayload.Decoded decoded = MoquettePayload.decode(message);
      ContextScope scope = DECORATE.receive(message, NettyUtils.clientID(channel), decoded);
      propagated = MoquettePayload.reframe(spanFromScope(scope), message, false, decoded);
      if (propagated != null) {
        message = propagated;
      }
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(
        @Advice.Enter ContextScope scope,
        @Advice.Thrown Throwable error,
        @Advice.Local("propagated") MqttPublishMessage propagated) {
      if (propagated != null) {
        propagated.release();
      }
      DECORATE.finish(scope, error);
    }
  }

  public static class DeliverAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ContextScope enter(
        @Advice.Argument(0) MqttMessage message, @Advice.FieldValue("channel") Channel channel) {
      if (message instanceof MqttPublishMessage) {
        return DECORATE.start(DELIVER, (MqttPublishMessage) message, NettyUtils.clientID(channel));
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter ContextScope scope, @Advice.Thrown Throwable error) {
      DECORATE.finish(scope, error);
    }
  }
}
