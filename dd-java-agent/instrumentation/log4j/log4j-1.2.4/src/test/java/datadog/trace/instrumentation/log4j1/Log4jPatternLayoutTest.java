package datadog.trace.instrumentation.log4j1;

import static datadog.trace.api.CorrelationIdentifier.getSpanId;
import static datadog.trace.api.CorrelationIdentifier.getTraceId;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static org.junit.jupiter.api.Assertions.assertEquals;

import datadog.context.ContextScope;
import datadog.trace.agent.test.AbstractInstrumentationTest;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.test.junit.utils.config.WithConfig;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@WithConfig(key = "logs.pattern.inject", value = "true")
@WithConfig(key = "service", value = "pattern-test")
class Log4jPatternLayoutTest extends AbstractInstrumentationTest {
  private static final String FIELDS =
      "dd.service=%X{dd.service} dd.trace_id=%X{dd.trace_id} dd.span_id=%X{dd.span_id}";
  private Capture capture;

  @BeforeEach
  void setup() {
    if (MDC.getContext() != null) {
      MDC.getContext().clear();
    }
    capture = new Capture();
  }

  @AfterEach
  void cleanup() {
    capture.logger.removeAllAppenders();
    if (MDC.getContext() != null) {
      MDC.getContext().clear();
    }
  }

  @Test
  void constructorAndSetterUseTheSameRules() {
    PatternLayout layout = new PatternLayout("[%d{yyyy-MM-dd HH:mm:ss}] %m%n");
    assertEquals("[%d{yyyy-MM-dd HH:mm:ss}] " + FIELDS + " %m%n", layout.getConversionPattern());
    layout.setConversionPattern(layout.getConversionPattern());
    assertEquals("[%d{yyyy-MM-dd HH:mm:ss}] " + FIELDS + " %m%n", layout.getConversionPattern());
    layout.setConversionPattern("%p %m%n");
    assertEquals("%p " + FIELDS + " %m%n", layout.getConversionPattern());
    assertEquals("%m " + FIELDS + "%n", new PatternLayout().getConversionPattern());
  }

  @Test
  @WithConfig(key = "logs.pattern.inject", value = "false")
  void disabled() {
    assertEquals("%m%n", new PatternLayout("%m%n").getConversionPattern());
  }

  @Test
  void capturesIdsForDeferredFormatting() {
    PatternLayout layout = new PatternLayout("%p %m%n");
    AgentSpan span = startSpan("test", "request");
    String traceId;
    String spanId;
    try (ContextScope ignored = activateSpan(span)) {
      traceId = getTraceId();
      spanId = getSpanId();
      capture.logger.info("message");
    } finally {
      span.finish();
    }
    assertEquals(
        "INFO dd.service=pattern-test dd.trace_id="
            + traceId
            + " dd.span_id="
            + spanId
            + " message"
            + System.lineSeparator(),
        layout.format(capture.event));
  }

  @Test
  void noSpan() {
    capture.logger.info("message");
    assertEquals(
        "INFO dd.service=pattern-test dd.trace_id= dd.span_id= message" + System.lineSeparator(),
        new PatternLayout("%p %m%n").format(capture.event));
  }

  @Test
  void applicationServiceWins() {
    MDC.put("dd.service", "application-service");
    capture.logger.info("message");
    assertEquals(
        "INFO dd.service=application-service dd.trace_id= dd.span_id= message"
            + System.lineSeparator(),
        new PatternLayout("%p %m%n").format(capture.event));
  }

  @Test
  void wholeMdcIsOnlyTreatedAsExistingWhenSupported() throws Exception {
    boolean fullMdc;
    try {
      LoggingEvent.class.getMethod("getProperties");
      fullMdc = true;
    } catch (NoSuchMethodException legacyVersion) {
      fullMdc = false;
    }
    assertEquals(
        fullMdc ? "%X %m%n" : "%X " + FIELDS + " %m%n",
        new PatternLayout("%X %m%n").getConversionPattern());
  }

  private static class Capture extends AppenderSkeleton {
    private final Logger logger = Logger.getLogger(Log4jPatternLayoutTest.class);
    private LoggingEvent event;

    private Capture() {
      logger.setAdditivity(false);
      logger.addAppender(this);
    }

    @Override
    protected void append(LoggingEvent event) {
      event.getMDCCopy();
      this.event = event;
    }

    @Override
    public void close() {}

    @Override
    public boolean requiresLayout() {
      return false;
    }
  }
}
