package datadog.trace.instrumentation.mqttv5;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;
import static datadog.trace.instrumentation.mqttv5.MqttDecorator.SUBSCRIBE_DECORATOR;

import datadog.context.ContextScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import net.bytebuddy.asm.Advice;
import org.eclipse.paho.mqttv5.common.MqttMessage;

public class MqttCallBackAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static ContextScope execute(
      @Advice.Argument(0) String topic, @Advice.Argument(1) MqttMessage message) {
    AgentSpan span = SUBSCRIBE_DECORATOR.createCallBackSpan(topic, message);
    ContextScope agentScope = activateSpan(span);

    return agentScope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void exit(
      @Advice.Enter final ContextScope scope, @Advice.Thrown final Throwable throwable) {
    if (scope == null) {
      return;
    }
    SUBSCRIBE_DECORATOR.onError(spanFromScope(scope), throwable);
    SUBSCRIBE_DECORATOR.beforeFinish(spanFromScope(scope));
    scope.close();
    spanFromScope(scope).finish();
  }
}
