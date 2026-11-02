package datadog.trace.instrumentation.pulsar;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import datadog.context.ContextScope;
import datadog.trace.agent.test.AbstractInstrumentationTest;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.junit.jupiter.api.Test;

class PulsarScopeTest extends AbstractInstrumentationTest {
  @Test
  void listenerPreservesBusinessExceptionAndParent() {
    IllegalStateException failure = new IllegalStateException("business failed");
    MessageListenerWrapper<String> listener =
        new MessageListenerWrapper<>(
            (c, m) -> {
              throw failure;
            });
    AgentSpan parent = startSpan("test", "parent");
    try (ContextScope ignored = activateSpan(parent)) {
      assertSame(
          failure,
          assertThrows(IllegalStateException.class, () -> listener.received(null, message())));
      assertSame(parent, activeSpan());
    } finally {
      parent.finish();
    }
  }

  @Test
  void listenerSuccessRestoresParent() {
    Message<String> message = message();
    MessageListenerWrapper<String> listener =
        new MessageListenerWrapper<>(
            (c, m) -> {
              assertSame(message, m);
              assertNotNull(activeSpan());
            });
    AgentSpan parent = startSpan("test", "parent");
    try (ContextScope ignored = activateSpan(parent)) {
      listener.received(null, message);
      assertSame(parent, activeSpan());
    } finally {
      parent.finish();
    }
  }

  @Test
  void realProducerSynchronousRejectionRestoresParent() throws Exception {
    org.apache.pulsar.client.impl.ProducerImpl<?> producer =
        new org.objenesis.ObjenesisStd()
            .newInstance(org.apache.pulsar.client.impl.ProducerImpl.class);
    int storeId =
        datadog.trace.bootstrap.FieldBackedContextStores.getContextStoreId(
            "org.apache.pulsar.client.impl.ProducerImpl", ProducerData.class.getName());
    datadog.trace.bootstrap.FieldBackedContextStores.getContextStore(storeId)
        .put(producer, ProducerData.create("pulsar://localhost:6650", "topic"));
    AgentSpan parent = startSpan("test", "parent");
    try (ContextScope ignored = activateSpan(parent)) {
      assertThrows(
          IllegalArgumentException.class, () -> producer.sendAsync(message(), new Callback()));
      assertSame(parent, activeSpan());
    } finally {
      parent.finish();
    }
    writer.waitForTraces(1);
    assertEquals(2, writer.get(0).size());
  }

  @Test
  void asyncAndInlineCallbacksRestoreParentAndFinishOnce() throws Exception {
    for (boolean inline : new boolean[] {false, true}) {
      AgentSpan parent = startSpan("test", "parent");
      AgentSpan send;
      final java.util.concurrent.atomic.AtomicInteger calls =
          new java.util.concurrent.atomic.AtomicInteger();
      try (ContextScope ignored = activateSpan(parent)) {
        send = startSpan("pulsar", "topic send");
        SendCallbackWrapper wrapper =
            new SendCallbackWrapper(
                send,
                new Callback() {
                  @Override
                  public void sendComplete(Exception error) {
                    assertSame(send, activeSpan());
                    calls.incrementAndGet();
                  }
                });
        try (ContextScope sendScope = activateSpan(send)) {
          if (inline) wrapper.sendComplete(null);
        }
        wrapper.onSendExit();
        assertSame(parent, activeSpan());
        if (!inline) {
          java.util.concurrent.atomic.AtomicReference<Throwable> failure =
              new java.util.concurrent.atomic.AtomicReference<>();
          Thread worker =
              new Thread(
                  () -> {
                    try {
                      AgentSpan before = activeSpan();
                      wrapper.sendComplete(null);
                      assertSame(before, activeSpan());
                    } catch (Throwable t) {
                      failure.set(t);
                    }
                  });
          worker.start();
          worker.join(5000);
          assertFalse(worker.isAlive());
          assertNull(failure.get());
        }
        assertEquals(1, calls.get());
      } finally {
        parent.finish();
      }
    }
    writer.waitForTraces(2);
    assertEquals(2, writer.get(0).size());
    assertEquals(2, writer.get(1).size());
  }

  @Test
  void callbackExceptionAndSendRejectionFinishAndRestoreParent() throws Exception {
    AgentSpan parent = startSpan("test", "parent");
    RuntimeException failure = new IllegalStateException("callback failed");
    try (ContextScope ignored = activateSpan(parent)) {
      AgentSpan send = startSpan("pulsar", "topic send");
      SendCallbackWrapper wrapper =
          new SendCallbackWrapper(
              send,
              new Callback() {
                @Override
                public void sendComplete(Exception error) {
                  throw failure;
                }
              });
      wrapper.onSendExit();
      assertSame(
          failure, assertThrows(IllegalStateException.class, () -> wrapper.sendComplete(null)));
      assertSame(parent, activeSpan());
      AgentSpan rejected = startSpan("pulsar", "rejected send");
      SendCallbackWrapper rejectedWrapper = new SendCallbackWrapper(rejected, new Callback());
      try (ContextScope sendScope = activateSpan(rejected)) {
        rejectedWrapper.onSendError(failure);
      }
      rejectedWrapper.onSendExit();
      assertSame(parent, activeSpan());
    } finally {
      parent.finish();
    }
    writer.waitForTraces(1);
    assertEquals(3, writer.get(0).size());
  }

  static class Callback implements org.apache.pulsar.client.impl.SendCallback {
    public void sendComplete(Exception error) {}

    public void addCallback(
        org.apache.pulsar.client.impl.MessageImpl<?> message,
        org.apache.pulsar.client.impl.SendCallback callback) {}

    public org.apache.pulsar.client.impl.SendCallback getNextSendCallback() {
      return null;
    }

    public org.apache.pulsar.client.impl.MessageImpl<?> getNextMessage() {
      return null;
    }

    public java.util.concurrent.CompletableFuture<MessageId> getFuture() {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  static Message<String> message() {
    Map<String, String> headers = new HashMap<>();
    headers.put("x-datadog-trace-id", "123");
    headers.put("x-datadog-parent-id", "456");
    return (Message<String>)
        Proxy.newProxyInstance(
            PulsarScopeTest.class.getClassLoader(),
            new Class<?>[] {Message.class},
            (o, m, a) -> {
              switch (m.getName()) {
                case "getProperties":
                  return headers;
                case "getTopicName":
                  return "topic";
                case "getData":
                  return new byte[] {1};
                case "getMessageId":
                  return MessageId.latest;
                default:
                  return null;
              }
            });
  }
}
