package datadog.trace.instrumentation.rocketmq;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import datadog.context.ContextScope;
import datadog.trace.agent.test.AbstractInstrumentationTest;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.Test;

class RocketMqScopeTest extends AbstractInstrumentationTest {
  @SuppressWarnings({"unchecked", "rawtypes"})
  @Test
  void asyncHookDoesNotTransferScopeToCompletionThread() throws Exception {
    Store store = new Store();
    SendMessageHook hook = RocketMqHook.buildSendHook((ContextStore) store);
    SendMessageContext context = new SendMessageContext();
    context.setMessage(new Message("topic", new byte[] {1}));
    AgentSpan parent = startSpan("test", "parent");
    try (ContextScope ignored = activateSpan(parent)) {
      hook.sendMessageBefore(context);
      assertSame(parent, activeSpan());
      assertNotNull(context.getMessage().getProperty("x-datadog-trace-id"));
    } finally {
      parent.finish();
    }
    Thread worker =
        new Thread(
            () -> {
              hook.sendMessageAfter(context);
              assertNull(activeSpan());
            });
    worker.start();
    worker.join(5000);
    assertFalse(worker.isAlive());
    assertNull(activeSpan());
    assertNull(store.get(context));
    hook.sendMessageAfter(context);
    writer.waitForTraces(1);
    assertEquals(2, writer.get(0).size());
  }

  static class Store implements ContextStore<SendMessageContext, Object> {
    final Map<SendMessageContext, Object> values = new IdentityHashMap<>();

    public Object get(SendMessageContext key) {
      return values.get(key);
    }

    public void put(SendMessageContext key, Object value) {
      values.put(key, value);
    }

    public Object getOrPut(SendMessageContext key, Object value) {
      Object old = values.get(key);
      if (old == null) values.put(key, value);
      return old == null ? value : old;
    }

    public Object getOrCompute(
        SendMessageContext key, Function<? super SendMessageContext, Object> factory) {
      return getOrPut(key, factory.apply(key));
    }

    public Object remove(SendMessageContext key) {
      return values.remove(key);
    }
  }
}
