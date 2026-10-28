package datadog.trace.instrumentation.log4j1;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static org.junit.jupiter.api.Assertions.assertEquals;

import datadog.context.ContextScope;
import datadog.trace.agent.test.AbstractInstrumentationTest;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.test.junit.utils.config.WithConfig;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

@WithConfig(key = "logs.pattern.inject", value = "true")
@WithConfig(key = "logs.injection.enabled", value = "false")
class Log4jPatternLayoutLogsInjectionDisabledTest extends AbstractInstrumentationTest {
  @Test
  void respectsDisabledMdcInjection() {
    if (MDC.getContext() != null) {
      MDC.getContext().clear();
    }
    Logger logger = Logger.getLogger(getClass());
    logger.setAdditivity(false);
    PatternLayout patternLayout = new PatternLayout("%p %m%n");
    AtomicReference<String> output = new AtomicReference<>();
    AppenderSkeleton appender =
        new AppenderSkeleton() {
          @Override
          protected void append(LoggingEvent event) {
            output.set(patternLayout.format(event));
          }

          @Override
          public void close() {}

          @Override
          public boolean requiresLayout() {
            return false;
          }
        };
    logger.addAppender(appender);
    AgentSpan span = startSpan("test", "request");
    try (ContextScope ignored = activateSpan(span)) {
      logger.info("message");
      assertEquals(
          "INFO dd.service= dd.trace_id= dd.span_id= message" + System.lineSeparator(),
          output.get());
    } finally {
      span.finish();
      logger.removeAppender(appender);
    }
  }
}
