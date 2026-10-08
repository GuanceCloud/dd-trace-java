package datadog.trace.instrumentation.logback;

import static datadog.trace.api.CorrelationIdentifier.getSpanId;
import static datadog.trace.api.CorrelationIdentifier.getTraceId;
import static datadog.trace.api.TracePropagationStyle.DATADOG;
import static datadog.trace.api.sampling.PrioritySampling.SAMPLER_KEEP;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.read.ListAppender;
import datadog.trace.agent.test.AbstractInstrumentationTest;
import datadog.trace.api.DDTraceId;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.core.propagation.ExtractedContext;
import datadog.trace.core.propagation.PropagationTags;
import datadog.trace.test.junit.utils.config.WithConfig;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@WithConfig(key = "logs.injection.enabled", value = "true")
class LogbackCorrelationTest extends AbstractInstrumentationTest {
  private LogCapture logs;

  private static class LogCapture {
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
  }

  @BeforeEach
  void setupLogger() {
    MDC.clear();
    logs = new LogCapture();
    logs.logger = (Logger) LoggerFactory.getLogger(getClass());
    logs.logger.setLevel(Level.DEBUG);
    logs.logger.setAdditive(false);
    logs.appender = new ListAppender<>();
    logs.appender.setContext(logs.logger.getLoggerContext());
    logs.appender.start();
    logs.logger.addAppender(logs.appender);
  }

  @AfterEach
  void cleanupLogger() {
    logs.logger.detachAppender(logs.appender);
    logs.appender.stop();
    MDC.clear();
  }

  @Test
  void replacesZeroIdsWithCapturedSpanForDebugAndInfoLogs() {
    MDC.put("dd.trace_id", "0");
    MDC.put("dd.span_id", "0");
    MDC.put("business.key", "value");
    AgentSpan span = startSpan("test", "request");
    String traceId;
    String spanId;
    try (AgentScope ignored = activateSpan(span)) {
      traceId = getTraceId();
      spanId = getSpanId();
      logs.logger.debug("debug message");
      logs.logger.info("info message");
      for (ILoggingEvent event : logs.appender.list) {
        event.prepareForDeferredProcessing();
      }
      assertEquals("0", MDC.get("dd.trace_id"));
      assertEquals("0", MDC.get("dd.span_id"));
    } finally {
      span.finish();
    }

    for (ILoggingEvent event : logs.appender.list) {
      Map<String, String> mdc = event.getMDCPropertyMap();
      assertEquals(traceId, mdc.get("dd.trace_id"));
      assertEquals(spanId, mdc.get("dd.span_id"));
      assertEquals("value", mdc.get("business.key"));
    }
    assertEquals(2, logs.appender.list.size());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void replacesBothIdsWhenOnlyOneIdIsZero(boolean zeroTraceId) {
    MDC.put("dd.trace_id", zeroTraceId ? "0" : "123");
    MDC.put("dd.span_id", zeroTraceId ? "456" : "0");
    AgentSpan span = startSpan("test", "request");
    try (AgentScope ignored = activateSpan(span)) {
      logs.logger.info("message");
      Map<String, String> mdc = logs.appender.list.get(0).getMDCPropertyMap();
      assertEquals(getTraceId(), mdc.get("dd.trace_id"));
      assertEquals(getSpanId(), mdc.get("dd.span_id"));
    } finally {
      span.finish();
    }
  }

  @Test
  void preservesExplicitNonzeroCorrelationIds() {
    MDC.put("dd.trace_id", "123");
    MDC.put("dd.span_id", "456");
    AgentSpan span = startSpan("test", "request");
    try (AgentScope ignored = activateSpan(span)) {
      logs.logger.info("message");
      Map<String, String> mdc = logs.appender.list.get(0).getMDCPropertyMap();
      assertEquals("123", mdc.get("dd.trace_id"));
      assertEquals("456", mdc.get("dd.span_id"));
    } finally {
      span.finish();
    }
  }

  @Test
  void replacesZeroIdsForAnAsyncAppender() throws InterruptedException {
    CountDownLatch received = new CountDownLatch(1);
    AtomicReference<Map<String, String>> captured = new AtomicReference<>();
    AppenderBase<ILoggingEvent> consumer =
        new AppenderBase<ILoggingEvent>() {
          @Override
          protected void append(ILoggingEvent event) {
            captured.set(event.getMDCPropertyMap());
            received.countDown();
          }
        };
    consumer.setContext(logs.logger.getLoggerContext());
    consumer.start();
    AsyncAppender async = new AsyncAppender();
    async.setContext(logs.logger.getLoggerContext());
    async.addAppender(consumer);
    async.start();
    logs.logger.addAppender(async);
    MDC.put("dd.trace_id", "0");
    MDC.put("dd.span_id", "0");
    AgentSpan span = startSpan("test", "request");
    String traceId;
    String spanId;
    try {
      try (AgentScope ignored = activateSpan(span)) {
        traceId = getTraceId();
        spanId = getSpanId();
        logs.logger.info("async message");
      } finally {
        span.finish();
      }
      assertTrue(received.await(10, TimeUnit.SECONDS), "async appender did not receive the log");
      assertEquals(traceId, captured.get().get("dd.trace_id"));
      assertEquals(spanId, captured.get().get("dd.span_id"));
    } finally {
      logs.logger.detachAppender(async);
      async.stop();
      consumer.stop();
    }
  }

  @Test
  void keepsCapturedIdsWhenFormattedUnderAnotherSpan() {
    AgentSpan firstSpan = startSpan("test", "first-request");
    String traceId;
    String spanId;
    try (AgentScope ignored = activateSpan(firstSpan)) {
      traceId = getTraceId();
      spanId = getSpanId();
      logs.logger.info("message");
    } finally {
      firstSpan.finish();
    }
    AgentSpan secondSpan = startSpan("test", "second-request");
    try (AgentScope ignored = activateSpan(secondSpan)) {
      Map<String, String> mdc = logs.appender.list.get(0).getMDCPropertyMap();
      assertEquals(traceId, mdc.get("dd.trace_id"));
      assertEquals(spanId, mdc.get("dd.span_id"));
    } finally {
      secondSpan.finish();
    }
  }

  @Test
  void doesNotInventIdsWithoutAnActiveSpan() {
    logs.logger.info("background message");
    Map<String, String> mdc = logs.appender.list.get(0).getMDCPropertyMap();
    assertNull(mdc.get("dd.trace_id"));
    assertNull(mdc.get("dd.span_id"));
  }

  @Test
  void leavesZeroIdsUntouchedWithoutAnActiveSpan() {
    MDC.put("dd.trace_id", "0");
    MDC.put("dd.span_id", "0");
    logs.logger.info("background message");
    Map<String, String> mdc = logs.appender.list.get(0).getMDCPropertyMap();
    assertEquals("0", mdc.get("dd.trace_id"));
    assertEquals("0", mdc.get("dd.span_id"));
  }

  @Test
  @WithConfig(key = "trace.128.bit.traceid.logging.enabled", value = "true")
  void replacesZeroIdsWith128BitTraceId() {
    assert128BitCorrelation(true);
  }

  @Test
  @WithConfig(key = "trace.128.bit.traceid.logging.enabled", value = "false")
  void usesDecimalTraceIdWhen128BitLoggingIsDisabled() {
    assert128BitCorrelation(false);
  }

  private void assert128BitCorrelation(boolean hexTraceId) {
    DDTraceId traceId = DDTraceId.fromHex("1234567890abcdef000000000000007b");
    ExtractedContext parent =
        new ExtractedContext(
            traceId, 456, SAMPLER_KEEP, null, PropagationTags.factory().empty(), DATADOG);
    MDC.put("dd.trace_id", "0");
    MDC.put("dd.span_id", "0");
    AgentSpan span = startSpan("test", "request", parent);
    try (AgentScope ignored = activateSpan(span)) {
      logs.logger.info("captured message");
      // An event constructed outside callAppenders exercises the active-span fallback.
      LoggingEvent fallback =
          new LoggingEvent(getClass().getName(), logs.logger, Level.INFO, "fallback", null, null);
      String expectedTraceId = hexTraceId ? traceId.toHexString() : traceId.toString();
      assertEquals(
          expectedTraceId, logs.appender.list.get(0).getMDCPropertyMap().get("dd.trace_id"));
      assertEquals(getSpanId(), logs.appender.list.get(0).getMDCPropertyMap().get("dd.span_id"));
      assertEquals(expectedTraceId, fallback.getMDCPropertyMap().get("dd.trace_id"));
      assertEquals(getSpanId(), fallback.getMDCPropertyMap().get("dd.span_id"));
    } finally {
      span.finish();
    }
  }
}
