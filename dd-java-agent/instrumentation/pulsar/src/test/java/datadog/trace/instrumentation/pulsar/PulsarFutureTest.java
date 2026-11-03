package datadog.trace.instrumentation.pulsar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datadog.trace.agent.test.AbstractInstrumentationTest;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Messages;
import org.junit.jupiter.api.Test;

class PulsarFutureTest extends AbstractInstrumentationTest {
  @Test
  void singleFailurePreservesTheOriginalException() {
    CompletableFuture<Message<?>> source = new CompletableFuture<>();
    CompletableFuture<Message<?>> result = ConsumerDecorator.wrap(source, "broker");
    RuntimeException failure = new IllegalStateException("receive failed");
    source.completeExceptionally(failure);
    assertTrue(result.isDone());
    assertSame(failure, assertThrows(CompletionException.class, result::join).getCause());
  }

  @Test
  void batchFailurePreservesTheOriginalException() {
    CompletableFuture<Messages<?>> source = new CompletableFuture<>();
    CompletableFuture<Messages<?>> result = ConsumerDecorator.wrapBatch(source, "broker");
    RuntimeException failure = new IllegalStateException("batch failed");
    source.completeExceptionally(failure);
    assertTrue(result.isDone());
    assertSame(failure, assertThrows(CompletionException.class, result::join).getCause());
  }

  @Test
  void singleCancellationIsPreservedInBothDirections() {
    CompletableFuture<Message<?>> source = new CompletableFuture<>();
    CompletableFuture<Message<?>> result = ConsumerDecorator.wrap(source, "broker");
    source.cancel(false);
    assertTrue(result.isCancelled());
    source = new CompletableFuture<>();
    result = ConsumerDecorator.wrap(source, "broker");
    result.cancel(false);
    assertTrue(source.isCancelled());
  }

  @Test
  void batchCancellationIsPreservedInBothDirections() {
    CompletableFuture<Messages<?>> source = new CompletableFuture<>();
    CompletableFuture<Messages<?>> result = ConsumerDecorator.wrapBatch(source, "broker");
    source.cancel(false);
    assertTrue(result.isCancelled());
    source = new CompletableFuture<>();
    result = ConsumerDecorator.wrapBatch(source, "broker");
    result.cancel(false);
    assertTrue(source.isCancelled());
  }

  @Test
  void nullResultsCompleteNormally() {
    CompletableFuture<Message<?>> result =
        ConsumerDecorator.wrap(CompletableFuture.completedFuture(null), "broker");
    assertTrue(result.isDone());
    assertNull(result.join());
    CompletableFuture<Messages<?>> batch =
        ConsumerDecorator.wrapBatch(CompletableFuture.completedFuture(null), "broker");
    assertTrue(batch.isDone());
    assertNull(batch.join());
  }

  @Test
  void tracingFailureCannotPreventMessageDelivery() {
    Message<?> message = message(true);
    CompletableFuture<Message<?>> source = new CompletableFuture<>();
    CompletableFuture<Message<?>> result = ConsumerDecorator.wrap(source, "broker");
    source.complete(message);
    assertTrue(result.isDone());
    assertSame(message, result.join());
  }

  @Test
  void successfulReceiveKeepsTheOriginalMessage() {
    Message<?> message = message(false);
    CompletableFuture<Message<?>> source = new CompletableFuture<>();
    CompletableFuture<Message<?>> result = ConsumerDecorator.wrap(source, "broker");
    assertFalse(result.isDone());
    source.complete(message);
    assertSame(message, result.join());
  }

  @Test
  void successfulBatchKeepsTheMessagesAndTracesEachOne() throws Exception {
    java.util.List<Message<?>> messages = java.util.Arrays.asList(message(false), message(false));
    Messages<?> batch =
        (Messages<?>)
            Proxy.newProxyInstance(
                PulsarFutureTest.class.getClassLoader(),
                new Class<?>[] {Messages.class},
                (p, m, a) -> {
                  switch (m.getName()) {
                    case "iterator":
                      return messages.iterator();
                    case "size":
                      return messages.size();
                    default:
                      return null;
                  }
                });
    CompletableFuture<Messages<?>> source = new CompletableFuture<>();
    CompletableFuture<Messages<?>> result = ConsumerDecorator.wrapBatch(source, "broker");
    source.complete(batch);
    assertSame(batch, result.join());
    writer.waitForTraces(2);
  }

  @Test
  void absentFutureDoesNotMaskAnSdkException() {
    assertNull(ConsumerDecorator.wrap(null, "broker"));
    assertNull(ConsumerDecorator.wrapBatch(null, "broker"));
  }

  @Test
  void lateMetadataFailureStillFinishesTheReceiveSpan() {
    Message<?> message =
        (Message<?>)
            Proxy.newProxyInstance(
                PulsarFutureTest.class.getClassLoader(),
                new Class<?>[] {Message.class},
                (p, m, a) -> {
                  switch (m.getName()) {
                    case "getProperties":
                      return Collections.emptyMap();
                    case "getTopicName":
                      return "topic";
                    case "getMessageId":
                      throw new IllegalStateException("late metadata failed");
                    default:
                      return null;
                  }
                });
    CompletableFuture<Message<?>> result =
        ConsumerDecorator.wrap(CompletableFuture.completedFuture(message), "broker");
    assertSame(message, result.join());
    assertEquals(1, writer.size());
  }

  static Message<?> message(boolean failMetadata) {
    return (Message<?>)
        Proxy.newProxyInstance(
            PulsarFutureTest.class.getClassLoader(),
            new Class<?>[] {Message.class},
            (p, m, a) -> {
              switch (m.getName()) {
                case "getProperties":
                  if (failMetadata) throw new IllegalStateException("metadata failed");
                  return Collections.emptyMap();
                case "getTopicName":
                  return "topic";
                case "getMessageId":
                  return MessageId.latest;
                default:
                  return null;
              }
            });
  }
}
