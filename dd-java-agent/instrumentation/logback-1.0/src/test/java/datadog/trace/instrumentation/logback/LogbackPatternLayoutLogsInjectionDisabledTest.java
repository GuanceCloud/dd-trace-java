package datadog.trace.instrumentation.logback;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.LoggingEvent;
import datadog.context.ContextScope;
import datadog.trace.agent.test.AbstractInstrumentationTest;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.test.junit.utils.config.WithConfig;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@WithConfig(key = "logs.pattern.inject", value = "true")
@WithConfig(key = "logs.injection.enabled", value = "false")
class LogbackPatternLayoutLogsInjectionDisabledTest extends AbstractInstrumentationTest {
  @Test
  void doesNotInventIdsWhenLogsInjectionIsDisabled() {
    MDC.clear();
    Logger logger = (Logger) LoggerFactory.getLogger(getClass());
    PatternLayout layout = new PatternLayout();
    layout.setContext(logger.getLoggerContext());
    layout.setPattern("%msg%n");
    layout.start();

    AgentSpan span = startSpan("test", "request");
    try (ContextScope ignored = activateSpan(span)) {
      LoggingEvent event =
          new LoggingEvent(getClass().getName(), logger, Level.INFO, "message", null, null);
      assertEquals(
          "dd.trace_id= dd.span_id= message" + System.lineSeparator(), layout.doLayout(event));
    } finally {
      span.finish();
      layout.stop();
      MDC.clear();
    }
  }
}
