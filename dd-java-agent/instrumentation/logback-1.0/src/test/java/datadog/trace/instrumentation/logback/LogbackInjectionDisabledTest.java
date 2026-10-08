package datadog.trace.instrumentation.logback;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import datadog.trace.agent.test.AbstractInstrumentationTest;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.test.junit.utils.config.WithConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@WithConfig(key = "logs.injection.enabled", value = "false")
class LogbackInjectionDisabledTest extends AbstractInstrumentationTest {
  @Test
  void leavesZeroIdsUntouchedWhenInjectionIsDisabled() {
    Logger logger = (Logger) LoggerFactory.getLogger(getClass());
    logger.setLevel(Level.INFO);
    logger.setAdditive(false);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.setContext(logger.getLoggerContext());
    appender.start();
    logger.addAppender(appender);
    MDC.put("dd.trace_id", "0");
    MDC.put("dd.span_id", "0");
    AgentSpan span = startSpan("test", "request");
    try (AgentScope ignored = activateSpan(span)) {
      logger.info("message");
      Map<String, String> mdc = appender.list.get(0).getMDCPropertyMap();
      assertEquals("0", mdc.get("dd.trace_id"));
      assertEquals("0", mdc.get("dd.span_id"));
    } finally {
      span.finish();
      logger.detachAppender(appender);
      appender.stop();
      MDC.clear();
    }
  }
}
