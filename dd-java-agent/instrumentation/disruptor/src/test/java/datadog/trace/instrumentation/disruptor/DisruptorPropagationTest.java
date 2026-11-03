package datadog.trace.instrumentation.disruptor;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lmax.disruptor.RingBuffer;
import datadog.context.ContextScope;
import datadog.trace.agent.test.AbstractInstrumentationTest;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.core.DDSpan;
import org.junit.jupiter.api.Test;
import test.disruptor.RecordingHandler;

class DisruptorPropagationTest extends AbstractInstrumentationTest {
  @Test
  void sequencesBeyondTheLongCacheAreTraced() throws Exception {
    RingBuffer<Object> ring = RingBuffer.createSingleProducer(Object::new, 256);
    RecordingHandler handler = new RecordingHandler();
    AgentSpan parent = startSpan("test", "parent");
    try (ContextScope ignored = activateSpan(parent)) {
      for (int i = 0; i < 260; i++) {
        long sequence = ring.next();
        ring.publish(sequence);
        handler.onEvent(ring.get(sequence), sequence, true);
        assertConsumer(handler);
        assertSame(parent, activeSpan());
      }
    } finally {
      parent.finish();
    }
    assertEquals(260, handler.calls);
    writer.waitForTraces(1);
    assertEquals(521, writer.get(0).size());
  }

  @Test
  void sameSequenceInDifferentRingsDoesNotCrossLink() throws Exception {
    RingBuffer<Object> first = RingBuffer.createSingleProducer(test.disruptor.EqualEvent::new, 16),
        second = RingBuffer.createSingleProducer(test.disruptor.EqualEvent::new, 16);
    RecordingHandler handler = new RecordingHandler();
    AgentSpan firstParent = startSpan("test", "first"), secondParent = startSpan("test", "second");
    long a = first.next(), b = second.next();
    try {
      try (ContextScope ignored = activateSpan(firstParent)) {
        first.publish(a);
      }
      try (ContextScope ignored = activateSpan(secondParent)) {
        second.publish(b);
      }
      handler.onEvent(first.get(a), a, true);
      assertConsumer(handler);
      assertEquals(firstParent.getTraceId(), handler.observed.getTraceId());
      handler.onEvent(second.get(b), b, true);
      assertConsumer(handler);
      assertEquals(secondParent.getTraceId(), handler.observed.getTraceId());
    } finally {
      firstParent.finish();
      secondParent.finish();
    }
  }

  @Test
  void translatedAndBatchPublishedEventsRetainTheirParent() throws Exception {
    RingBuffer<Object> ring = RingBuffer.createSingleProducer(Object::new, 16);
    RecordingHandler handler = new RecordingHandler();
    AgentSpan parent = startSpan("test", "parent");
    try (ContextScope ignored = activateSpan(parent)) {
      ring.publishEvent((event, sequence) -> {});
      long sequence = ring.getCursor();
      handler.onEvent(ring.get(sequence), sequence, true);
      assertConsumer(handler);
      long high = ring.next(2);
      ring.publish(high - 1, high);
      handler.onEvent(ring.get(high - 1), high - 1, false);
      assertConsumer(handler);
      handler.onEvent(ring.get(high), high, true);
      assertConsumer(handler);
      ring.publishEvents((event, seq, arg) -> {}, new String[] {"one", "two"});
      high = ring.getCursor();
      handler.onEvent(ring.get(high - 1), high - 1, false);
      assertConsumer(handler);
      handler.onEvent(ring.get(high), high, true);
      assertConsumer(handler);
    } finally {
      parent.finish();
    }
  }

  @Test
  void multipleHandlersAndBusinessExceptionsKeepTheirSemantics() throws Exception {
    RingBuffer<Object> ring = RingBuffer.createSingleProducer(Object::new, 16);
    RecordingHandler first = new RecordingHandler(), second = new RecordingHandler();
    RuntimeException failure = new IllegalStateException("handler failed");
    second.failure = failure;
    AgentSpan parent = startSpan("test", "parent");
    try (ContextScope ignored = activateSpan(parent)) {
      long sequence = ring.next();
      ring.publish(sequence);
      Object event = ring.get(sequence);
      first.onEvent(event, sequence, true);
      assertConsumer(first);
      assertSame(
          failure,
          assertThrows(IllegalStateException.class, () -> second.onEvent(event, sequence, true)));
      assertConsumer(second);
      assertSame(parent, activeSpan());
      assertEquals(
          ((DDSpan) first.observed).getParentId(), ((DDSpan) second.observed).getParentId());
    } finally {
      parent.finish();
    }
  }

  @Test
  void deliveryAfterTheProducerTraceFinishesStillCorrelates() throws Exception {
    RingBuffer<Object> ring = RingBuffer.createSingleProducer(Object::new, 16);
    long sequence = ring.next();
    ring.publish(sequence);
    RecordingHandler handler = new RecordingHandler();
    handler.onEvent(ring.get(sequence), sequence, true);
    assertConsumer(handler);
    assertNull(activeSpan());
    writer.waitForTraces(2);
  }

  @Test
  void typedHandlerBridgeDoesNotCreateDuplicateSpans() throws Exception {
    RingBuffer<test.disruptor.EqualEvent> ring =
        RingBuffer.createSingleProducer(test.disruptor.EqualEvent::new, 16);
    test.disruptor.TypedHandler handler = new test.disruptor.TypedHandler();
    com.lmax.disruptor.EventHandler<test.disruptor.EqualEvent> eventHandler = handler;
    AgentSpan parent = startSpan("test", "parent");
    try (ContextScope ignored = activateSpan(parent)) {
      long sequence = ring.next();
      ring.publish(sequence);
      eventHandler.onEvent(ring.get(sequence), sequence, true);
      assertNotNull(handler.observed);
      assertSame(parent, activeSpan());
    } finally {
      parent.finish();
    }
    writer.waitForTraces(1);
    assertEquals(3, writer.get(0).size());
  }

  @Test
  void actualConsumerThreadCorrelatesAcrossRingWraparound() throws Exception {
    RingBuffer<Object> ring = RingBuffer.createSingleProducer(Object::new, 64);
    RecordingHandler handler = new RecordingHandler();
    handler.done = new java.util.concurrent.CountDownLatch(300);
    com.lmax.disruptor.BatchEventProcessor<Object> processor =
        new com.lmax.disruptor.BatchEventProcessor<>(ring, ring.newBarrier(), handler);
    ring.addGatingSequences(processor.getSequence());
    Thread worker = new Thread(processor, "disruptor-regression");
    worker.start();
    AgentSpan parent = startSpan("test", "parent");
    try {
      try (ContextScope ignored = activateSpan(parent)) {
        for (int i = 0; i < 300; i++) {
          long sequence = ring.next();
          ring.publish(sequence);
        }
        assertTrue(handler.done.await(5, java.util.concurrent.TimeUnit.SECONDS));
        assertConsumer(handler);
        assertEquals(parent.getTraceId(), handler.observed.getTraceId());
        assertSame(parent, activeSpan());
      } finally {
        parent.finish();
      }
    } finally {
      processor.halt();
      worker.join(5000);
      assertFalse(worker.isAlive());
    }
    assertEquals(300, handler.calls);
    writer.waitForTraces(1);
    assertEquals(601, writer.get(0).size());
  }

  @Test
  void largeRingsKeepNewSlotsAndRefreshReusedSlots() throws Exception {
    RingBuffer<Object> ring = RingBuffer.createSingleProducer(Object::new, 65536);
    RecordingHandler handler = new RecordingHandler();
    AgentSpan first = startSpan("test", "first-large-ring"),
        second = startSpan("test", "second-large-ring");
    try {
      try (ContextScope ignored = activateSpan(first)) {
        long high = ring.next(65536);
        ring.publish(0, high);
        handler.onEvent(ring.get(high), high, true);
        assertConsumer(handler);
        assertEquals(first.getTraceId(), handler.observed.getTraceId());
      }
      try (ContextScope ignored = activateSpan(second)) {
        long sequence = ring.next();
        ring.publish(sequence);
        handler.onEvent(ring.get(sequence), sequence, true);
        assertConsumer(handler);
        assertEquals(second.getTraceId(), handler.observed.getTraceId());
      }
    } finally {
      first.finish();
      second.finish();
    }
  }

  static void assertConsumer(RecordingHandler handler) {
    assertNotNull(handler.observed);
    assertEquals("disruptor/consumer", handler.observed.getSpanName().toString());
  }
}
