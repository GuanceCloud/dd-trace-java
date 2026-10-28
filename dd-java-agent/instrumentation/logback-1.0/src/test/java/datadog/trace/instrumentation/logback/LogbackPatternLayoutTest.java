package datadog.trace.instrumentation.logback;

import static datadog.trace.api.CorrelationIdentifier.getSpanId;
import static datadog.trace.api.CorrelationIdentifier.getTraceId;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.OutputStreamAppender;
import datadog.context.ContextScope;
import datadog.trace.agent.test.AbstractInstrumentationTest;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.test.junit.utils.config.WithConfig;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.tabletest.junit.TableTest;

@WithConfig(key = "logs.pattern.inject", value = "true")
@WithConfig(key = "logs.injection.enabled", value = "true")
class LogbackPatternLayoutTest extends AbstractInstrumentationTest {
  private static final String TRACE = "dd.trace_id=%X{dd.trace_id} ";
  private static final String SPAN = "dd.span_id=%X{dd.span_id} ";

  private LogCapture logs;

  @BeforeEach
  void setupLogger() {
    MDC.clear();
    logs = new LogCapture();
  }

  @AfterEach
  void cleanupLogger() {
    logs.logger.detachAndStopAllAppenders();
    MDC.clear();
  }

  @TableTest({
    "Scenario       | Pattern                                         | Add trace | Add span",
    "plain          | %level %logger - %msg%n                         | true      | true    ",
    "trace only     | %X{dd.trace_id} %msg%n                          | false     | true    ",
    "span only      | %mdc{dd.span_id} %msg%n                         | true      | false   ",
    "both           | %X{dd.trace_id} %mdc{dd.span_id} %msg%n         | false     | false   ",
    "all MDC        | %X %msg%n                                       | false     | false   ",
    "empty options  | %mdc{} %msg%n                                   | false     | false   ",
    "empty MDC key  | %X{:-NA} %msg%n                                 | true      | true    ",
    "default values | %X{dd.trace_id:-NA} %mdc{dd.span_id:-NA} %msg%n | false     | false   ",
    "formatted      | %-32X{dd.trace_id} %20mdc{dd.span_id} %msg%n    | false     | false   ",
    "nested         | %replace(%X{dd.trace_id}){a,b} %msg%n           | false     | true    ",
    "escaped        | \\%X{dd.trace_id} %msg%n                        | true      | true    ",
    "literal keys   | dd.trace_id dd.span_id %msg%n                   | true      | true    ",
    "other MDC      | %X{business.key} %msg%n                         | true      | true    ",
    "multiline      | %msg%nsecond line%n                             | true      | true    "
  })
  void addsOnlyMissingFieldsAndDoesNotDuplicateOnRestart(
      String pattern, boolean addTrace, boolean addSpan) {
    PatternLayout layout = logs.newLayout(pattern);
    String expected = (addTrace ? TRACE : "") + (addSpan ? SPAN : "") + pattern;
    assertEquals(expected, layout.getPattern());
    assertTrue(layout.isStarted());

    layout.stop();
    layout.start();
    assertEquals(expected, layout.getPattern());
  }

  @Test
  void converterOptionsAreNotMistakenForMdcConverters() {
    String pattern = "%replace(%msg){'message','%X{dd.trace_id} %X{dd.span_id}'}%n";
    assertEquals(TRACE + SPAN + pattern, logs.newLayout(pattern).getPattern());
  }

  @Test
  @WithConfig(key = "logs.pattern.inject", value = "false")
  void keepsOriginalPatternWhenDisabled() {
    assertEquals("%msg%n", logs.newLayout("%msg%n").getPattern());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "%replace(%msg")
  void leavesInvalidPatternsToLogbackValidation(String pattern) {
    PatternLayout layout = logs.newLayout(pattern);
    assertEquals(pattern, layout.getPattern());
    assertFalse(layout.isStarted());
  }

  @Test
  void encoderIncludesActiveSpanIdsAndPreservesOriginalLogContent() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    encoder.setContext(logs.context);
    encoder.setPattern("%level - %msg%n");
    encoder.start();
    OutputStreamAppender<ILoggingEvent> appender = new OutputStreamAppender<>();
    appender.setContext(logs.context);
    appender.setEncoder(encoder);
    appender.setOutputStream(output);
    appender.start();
    logs.logger.addAppender(appender);

    AgentSpan span = startSpan("test", "request");
    String traceId;
    String spanId;
    try (ContextScope ignored = activateSpan(span)) {
      traceId = getTraceId();
      spanId = getSpanId();
      logs.logger.info("message");
    } finally {
      span.finish();
      appender.stop();
      encoder.stop();
    }
    assertEquals(
        "dd.trace_id="
            + traceId
            + " dd.span_id="
            + spanId
            + " INFO - message"
            + System.lineSeparator(),
        output.toString("UTF-8"));
  }

  @Test
  void asyncAppenderFormatsOriginatingSpanIds() throws InterruptedException {
    PatternLayout layout = logs.newLayout("%msg%n");
    CountDownLatch received = new CountDownLatch(1);
    AtomicReference<String> output = new AtomicReference<>();
    AppenderBase<ILoggingEvent> consumer =
        new AppenderBase<ILoggingEvent>() {
          @Override
          protected void append(ILoggingEvent event) {
            output.set(layout.doLayout(event));
            received.countDown();
          }
        };
    consumer.setContext(logs.context);
    consumer.start();
    AsyncAppender async = new AsyncAppender();
    async.setContext(logs.context);
    async.addAppender(consumer);
    async.start();
    logs.logger.addAppender(async);

    AgentSpan span = startSpan("test", "request");
    String traceId;
    String spanId;
    try {
      try (ContextScope ignored = activateSpan(span)) {
        traceId = getTraceId();
        spanId = getSpanId();
        logs.logger.info("async message");
      } finally {
        span.finish();
      }
      assertTrue(received.await(10, TimeUnit.SECONDS), "async appender did not receive the log");
      assertEquals(
          "dd.trace_id="
              + traceId
              + " dd.span_id="
              + spanId
              + " async message"
              + System.lineSeparator(),
          output.get());
    } finally {
      async.stop();
      consumer.stop();
    }
  }

  @Test
  void doesNotInventIdsWithoutActiveSpan() {
    assertEquals(
        "dd.trace_id= dd.span_id= background" + System.lineSeparator(),
        logs.newLayout("%msg%n").doLayout(logs.event("background")));
  }

  private static class LogCapture {
    private final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    private final Logger logger = context.getLogger(LogbackPatternLayoutTest.class);

    private LogCapture() {
      logger.setLevel(Level.DEBUG);
      logger.setAdditive(false);
    }

    private LoggingEvent event(String message) {
      return new LoggingEvent(getClass().getName(), logger, Level.INFO, message, null, null);
    }

    private PatternLayout newLayout(String pattern) {
      PatternLayout layout = new PatternLayout();
      layout.setContext(context);
      layout.setPattern(pattern);
      layout.start();
      return layout;
    }
  }
}
