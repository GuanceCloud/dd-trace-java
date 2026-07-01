package datadog.trace.agent.jmxfetch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datadog.metrics.api.statsd.StatsDClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JvmThreadCountStatsDReporterTest {

  @Test
  void reportOnceEmitsGaugeWithCommonAndThreadTags() {
    CapturingStatsDClient statsd = new CapturingStatsDClient();
    Map<String, String> commonTags = new LinkedHashMap<>();
    commonTags.put("service", "test-service");
    commonTags.put("env", "test");

    new JvmThreadCountStatsDReporter(statsd, commonTags, 1_000L, () -> true).reportOnce();

    assertFalse(statsd.gauges.isEmpty(), "Expected at least one gauge emission");
    for (Gauge gauge : statsd.gauges) {
      assertEquals(JvmThreadCountStatsDReporter.METRIC_NAME, gauge.metricName);
      assertTrue(gauge.value > 0, "Buckets should skip zero counts");
      assertContainsPrefix(gauge.tags, "service:test-service");
      assertContainsPrefix(gauge.tags, "env:test");
      assertContainsPrefix(gauge.tags, "jvm.thread.daemon:");
      assertContainsPrefix(gauge.tags, "jvm.thread.state:");
    }
  }

  private static void assertContainsPrefix(String[] tags, String expectedPrefix) {
    for (String tag : tags) {
      if (tag.startsWith(expectedPrefix)) {
        return;
      }
    }
    throw new AssertionError("Missing tag prefix " + expectedPrefix);
  }

  private static final class Gauge {
    private final String metricName;
    private final long value;
    private final String[] tags;

    private Gauge(String metricName, long value, String[] tags) {
      this.metricName = metricName;
      this.value = value;
      this.tags = tags;
    }
  }

  private static final class CapturingStatsDClient implements StatsDClient {
    private final List<Gauge> gauges = new ArrayList<>();

    @Override
    public void incrementCounter(String metricName, String... tags) {}

    @Override
    public void count(String metricName, long delta, String... tags) {}

    @Override
    public void gauge(String metricName, long value, String... tags) {
      gauges.add(new Gauge(metricName, value, tags));
    }

    @Override
    public void gauge(String metricName, double value, String... tags) {}

    @Override
    public void histogram(String metricName, long value, String... tags) {}

    @Override
    public void histogram(String metricName, double value, String... tags) {}

    @Override
    public void distribution(String metricName, long value, String... tags) {}

    @Override
    public void distribution(String metricName, double value, String... tags) {}

    @Override
    public void serviceCheck(
        String serviceCheckName, String status, String message, String... tags) {}

    @Override
    public void error(Exception error) {}

    @Override
    public int getErrorCount() {
      return 0;
    }

    @Override
    public void close() {}
  }
}
