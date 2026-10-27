package datadog.trace.instrumentation.rocketmq;

import static datadog.trace.instrumentation.rocketmq.RocketMqDecorator.PRODUCER_DECORATE;

import datadog.context.ContextScope;
import datadog.trace.bootstrap.ContextStore;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TracingSendMessageHookImpl implements SendMessageHook {

  private final RocketMqDecorator rocketMqDecorator;
  private static final Logger log = LoggerFactory.getLogger(TracingSendMessageHookImpl.class);
  private final ContextStore<SendMessageContext, ContextScope> store;

  TracingSendMessageHookImpl(ContextStore<SendMessageContext, ContextScope> store) {
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
    ContextScope scope = store.get(context);
    if (scope == null) {
      scope = rocketMqDecorator.start(context);
      store.getOrPut(context, scope);
    }
  }

  @Override
  public void sendMessageAfter(SendMessageContext context) {
    ContextScope scope = store.get(context);
    if (scope == null) {
      return;
    }

    rocketMqDecorator.end(context, scope);
    scope.close();
    if (log.isDebugEnabled()) {
      log.debug("scope close");
    }
  }
}
