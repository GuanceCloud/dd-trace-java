package datadog.trace.instrumentation.rocketmq;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.rocketmq.RocketMqDecorator.PRODUCER_DECORATE;

import datadog.context.ContextScope;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TracingSendMessageHookImpl implements SendMessageHook {

  private final RocketMqDecorator rocketMqDecorator;
  private static final Logger log = LoggerFactory.getLogger(TracingSendMessageHookImpl.class);
  private final ContextStore<SendMessageContext, AgentSpan> store;

  TracingSendMessageHookImpl(ContextStore<SendMessageContext, AgentSpan> store) {
    this.rocketMqDecorator = PRODUCER_DECORATE;
    this.store = store;
  }

  @Override
  public String hookName() {
    return "SendMessageTraceHook";
  }

  @Override
  public void sendMessageBefore(SendMessageContext context) {
    if (context == null) {
      return;
    }
    if (store.get(context) == null) {
      store.put(context, rocketMqDecorator.start(context));
    }
  }

  @Override
  public void sendMessageAfter(SendMessageContext context) {
    if (context == null) {
      return;
    }
    AgentSpan span = store.remove(context);
    if (span == null) {
      return;
    }
    try (ContextScope ignored = activateSpan(span)) {
      rocketMqDecorator.end(context, span);
    } finally {
      span.finish();
    }
  }
}
