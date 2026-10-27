package datadog.trace.instrumentation.rocketmq;

import datadog.context.ContextScope;
import datadog.trace.bootstrap.ContextStore;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.client.hook.ConsumeMessageHook;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;

public final class RocketMqHook {
  public static ConsumeMessageHook buildConsumerHook(
      ContextStore<ConsumeMessageContext, ContextScope> store) {
    return new TracingConsumeMessageHookImpl(store);
  }

  public static SendMessageHook buildSendHook(
      ContextStore<SendMessageContext, ContextScope> store) {
    return new TracingSendMessageHookImpl(store);
  }
}
