package datadog.trace.instrumentation.ons_client;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.OnExceptionContext;
import com.aliyun.openservices.ons.api.SendCallback;
import com.aliyun.openservices.ons.api.SendResult;
import com.aliyun.openservices.ons.api.order.MessageOrderListener;
import datadog.context.ContextScope;
import datadog.trace.agent.test.AbstractInstrumentationTest;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import test.guancescope.OrderedListener;
import test.guancescope.ProducerStub;

class OnsScopeTest extends AbstractInstrumentationTest {
  @Test
  void asyncReturnAndCallbackRestoreTheirOwnParents() throws Exception {
    ProducerStub producer = new ProducerStub();
    AgentSpan parent = startSpan("test", "parent");
    AtomicReference<AgentSpan> callbackSpan = new AtomicReference<>();
    try (ContextScope ignored = activateSpan(parent)) {
      producer.sendAsync(
          message(),
          new SendCallback() {
            public void onSuccess(SendResult result) {
              callbackSpan.set(activeSpan());
            }

            public void onException(OnExceptionContext context) {
              fail("unexpected error");
            }
          });
      assertSame(parent, activeSpan());
    } finally {
      parent.finish();
    }
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread worker =
        new Thread(
            () -> {
              AgentSpan workerParent = startSpan("test", "worker");
              try (ContextScope ignored = activateSpan(workerParent)) {
                producer.pending.onSuccess(new SendResult());
                assertSame(workerParent, activeSpan());
              } catch (Throwable t) {
                failure.set(t);
              } finally {
                workerParent.finish();
              }
            });
    worker.start();
    worker.join(5000);
    assertFalse(worker.isAlive());
    assertNull(failure.get());
    assertNotNull(callbackSpan.get());
    assertEquals(
        parent.getSpanId(), ((datadog.trace.core.DDSpan) callbackSpan.get()).getParentId());
    assertNull(activeSpan());
  }

  @Test
  void synchronousSendFailurePreservesExceptionAndParent() throws Exception {
    ProducerStub producer = new ProducerStub();
    producer.failure = new IllegalStateException("send failed");
    AgentSpan parent = startSpan("test", "parent");
    try (ContextScope ignored = activateSpan(parent)) {
      assertSame(
          producer.failure,
          assertThrows(
              IllegalStateException.class,
              () ->
                  producer.sendAsync(
                      message(),
                      new SendCallback() {
                        public void onSuccess(SendResult result) {}

                        public void onException(OnExceptionContext context) {}
                      })));
      assertSame(parent, activeSpan());
    } finally {
      parent.finish();
    }
    writer.waitForTraces(1);
    assertEquals(2, writer.get(0).size());
    assertTrue(writer.get(0).stream().anyMatch(span -> span.isError()));
  }

  @Test
  void orderedConsumerExceptionDoesNotSkipCleanup() {
    IllegalStateException failure = new IllegalStateException("consume failed");
    MessageOrderListener listener = new OrderedListener(failure);
    AgentSpan parent = startSpan("test", "parent");
    try (ContextScope ignored = activateSpan(parent)) {
      assertSame(
          failure,
          assertThrows(IllegalStateException.class, () -> listener.consume(message(), null)));
      assertSame(parent, activeSpan());
    } finally {
      parent.finish();
    }
  }

  @Test
  void inlineCallbackFinishesAfterSendScopeCloses() throws Exception {
    ProducerStub producer = new ProducerStub();
    producer.inline = true;
    AgentSpan parent = startSpan("test", "parent");
    java.util.concurrent.atomic.AtomicInteger calls =
        new java.util.concurrent.atomic.AtomicInteger();
    try (ContextScope ignored = activateSpan(parent)) {
      producer.sendAsync(
          message(),
          new SendCallback() {
            public void onSuccess(SendResult result) {
              calls.incrementAndGet();
              assertNotSame(parent, activeSpan());
            }

            public void onException(OnExceptionContext context) {
              fail("unexpected error");
            }
          });
      assertSame(parent, activeSpan());
      assertEquals(1, calls.get());
    } finally {
      parent.finish();
    }
    writer.waitForTraces(1);
    assertEquals(2, writer.get(0).size());
  }

  @Test
  void callbackFailurePreservesExceptionAndRestoresParent() throws Exception {
    ProducerStub producer = new ProducerStub();
    RuntimeException failure = new IllegalStateException("callback failed");
    producer.sendAsync(
        message(),
        new SendCallback() {
          public void onSuccess(SendResult result) {
            throw failure;
          }

          public void onException(OnExceptionContext context) {
            throw failure;
          }
        });
    AgentSpan parent = startSpan("test", "callback-parent");
    try (ContextScope ignored = activateSpan(parent)) {
      assertSame(
          failure,
          assertThrows(
              IllegalStateException.class, () -> producer.pending.onSuccess(new SendResult())));
      assertSame(parent, activeSpan());
    } finally {
      parent.finish();
    }
    writer.waitForTraces(2);
  }

  @Test
  void failedAsyncCallbackRestoresParent() throws Exception {
    ProducerStub producer = new ProducerStub();
    java.util.concurrent.atomic.AtomicInteger calls =
        new java.util.concurrent.atomic.AtomicInteger();
    producer.sendAsync(
        message(),
        new SendCallback() {
          public void onSuccess(SendResult result) {
            fail("unexpected success");
          }

          public void onException(OnExceptionContext context) {
            calls.incrementAndGet();
          }
        });
    OnExceptionContext error = new OnExceptionContext();
    error.setException(
        new com.aliyun.openservices.ons.api.exception.ONSClientException("broker failed"));
    producer.pending.onException(error);
    assertNull(activeSpan());
    assertEquals(1, calls.get());
    writer.waitForTraces(1);
    assertTrue(writer.get(0).get(0).isError());
  }

  static Message message() {
    return new Message("topic", "tag", new byte[] {1});
  }
}
