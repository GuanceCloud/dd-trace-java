package datadog.trace.instrumentation.rocketmq;

import static datadog.trace.instrumentation.rocketmq.RocketMqDecorator.CONSUMER_DECORATE;

import datadog.context.ContextScope;
import datadog.trace.bootstrap.ContextStore;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.client.hook.ConsumeMessageHook;

public final class TracingConsumeMessageHookImpl implements ConsumeMessageHook {
  private final RocketMqDecorator rocketMqDecorator;

  TracingConsumeMessageHookImpl(ContextStore<ConsumeMessageContext, ContextScope> store) {
    this.store = store;
    this.rocketMqDecorator = CONSUMER_DECORATE;
  }

  private final ContextStore<ConsumeMessageContext, ContextScope> store;

  @Override
  public String hookName() {
    return "ConsumeMessageTraceHook";
  }

  @Override
  public void consumeMessageBefore(ConsumeMessageContext context) {
    if (context == null || context.getMsgList() == null || context.getMsgList().isEmpty()) {
      return;
    }
    ContextScope scope = store.get(context);
    if (scope == null) {
      scope = rocketMqDecorator.start(context);
      store.getOrPut(context, scope);
    }
  }

  @Override
  public void consumeMessageAfter(ConsumeMessageContext context) {
    ContextScope scope = store.get(context);

    if (scope == null) {
      return;
    }

    rocketMqDecorator.end(context, scope);
    scope.close();
  }
}
