package datadog.trace.instrumentation.log4j;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static org.junit.jupiter.api.Assertions.assertEquals;

import datadog.context.ContextScope;
import datadog.trace.agent.test.AbstractInstrumentationTest;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.test.junit.utils.config.WithConfig;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.Test;

@WithConfig(key = "logs.pattern.inject", value = "true")
@WithConfig(key = "logs.injection.enabled", value = "false")
public abstract class Log4j2PatternLayoutLogsInjectionDisabledTestBase
    extends AbstractInstrumentationTest {
  @Test
  protected void respectsDisabledMdcInjection() {
    ThreadContext.clearAll();
    PatternLayout layout = PatternLayout.newBuilder().withPattern("%p %m%n").build();
    AgentSpan span = startSpan("test", "request");
    try (ContextScope ignored = activateSpan(span)) {
      Log4jLogEvent event =
          new Log4jLogEvent(
              "test", null, getClass().getName(), Level.INFO, new SimpleMessage("message"), null);
      assertEquals(
          "INFO dd.service= dd.trace_id= dd.span_id= message" + System.lineSeparator(),
          layout.toSerializable(event));
    } finally {
      span.finish();
      ThreadContext.clearAll();
    }
  }
}
