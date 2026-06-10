package datadog.trace.instrumentation.log4j27;

import static org.junit.jupiter.api.Assertions.assertEquals;

import datadog.trace.agent.test.AbstractInstrumentationTest;
import datadog.trace.junit.utils.config.WithConfig;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.Test;

@WithConfig(key = "logs.pattern.replace", value = "true")
@WithConfig(key = "logs.pattern", value = "replacement:%m%n")
class Log4j2PatternLayoutTest extends AbstractInstrumentationTest {

  @Test
  void replacesPatternLayoutPatternFromConfig() {
    PatternLayout layout =
        PatternLayout.newBuilder()
            .withPattern("original:%m%n")
            .withCharset(StandardCharsets.UTF_8)
            .build();
    LogEvent event =
        Log4jLogEvent.newBuilder()
            .setLoggerName("test")
            .setLevel(Level.INFO)
            .setMessage(new SimpleMessage("message"))
            .build();

    assertEquals(
        "replacement:message\n", new String(layout.toByteArray(event), StandardCharsets.UTF_8));
  }
}
