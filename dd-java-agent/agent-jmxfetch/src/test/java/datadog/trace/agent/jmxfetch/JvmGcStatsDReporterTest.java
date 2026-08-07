package datadog.trace.agent.jmxfetch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datadog.metrics.api.statsd.StatsDClient;
import java.lang.management.GarbageCollectorMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.management.ObjectName;
import org.junit.jupiter.api.Test;

class JvmGcStatsDReporterTest {

  @Test
  void reportOnceEmitsRawGcBeanValuesWithGcTag() {
    CapturingStatsDClient statsd = new CapturingStatsDClient();
    Map<String, String> commonTags = new LinkedHashMap<>();
    commonTags.put("service", "test-service");
    commonTags.put("env", "test");

    new JvmGcStatsDReporter(
            statsd,
            commonTags,
            1_000L,
            () -> true,
            Collections.singletonList(new TestGarbageCollectorMXBean()))
        .reportOnce();

    assertEquals(2, statsd.gauges.size());
    Gauge countGauge = statsd.gauges.get(0);
    assertEquals(JvmGcStatsDReporter.COLLECTION_COUNT_METRIC, countGauge.metricName);
    assertEquals(42, countGauge.value);
    assertContainsPrefix(countGauge.tags, "gc:test-gc");
    assertContainsPrefix(countGauge.tags, "service:test-service");
    assertContainsPrefix(countGauge.tags, "env:test");

    Gauge timeGauge = statsd.gauges.get(1);
    assertEquals(JvmGcStatsDReporter.COLLECTION_TIME_METRIC, timeGauge.metricName);
    assertEquals(1_234, timeGauge.value);
    assertContainsPrefix(timeGauge.tags, "gc:test-gc");
    assertContainsPrefix(timeGauge.tags, "service:test-service");
    assertContainsPrefix(timeGauge.tags, "env:test");
  }

  @Test
  void reportOnceSkipsUnavailableGcBeanValues() {
    CapturingStatsDClient statsd = new CapturingStatsDClient();

    new JvmGcStatsDReporter(
            statsd,
            Collections.emptyMap(),
            1_000L,
            () -> true,
            Collections.singletonList(new UnavailableGarbageCollectorMXBean()))
        .reportOnce();

    assertTrue(statsd.gauges.isEmpty());
  }

  @Test
  void reportOnceUsesJvmGcBeans() {
    CapturingStatsDClient statsd = new CapturingStatsDClient();

    new JvmGcStatsDReporter(statsd, Collections.emptyMap(), 1_000L, () -> true).reportOnce();

    assertFalse(statsd.gauges.isEmpty(), "Expected at least one GC MXBean gauge emission");
    for (Gauge gauge : statsd.gauges) {
      assertContainsPrefix(gauge.tags, "gc:");
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

  private static class TestGarbageCollectorMXBean implements GarbageCollectorMXBean {
    @Override
    public String getName() {
      return "test-gc";
    }

    @Override
    public long getCollectionCount() {
      return 42;
    }

    @Override
    public long getCollectionTime() {
      return 1_234;
    }

    @Override
    public String[] getMemoryPoolNames() {
      return new String[0];
    }

    @Override
    public boolean isValid() {
      return true;
    }

    @Override
    public ObjectName getObjectName() {
      return null;
    }
  }

  private static final class UnavailableGarbageCollectorMXBean extends TestGarbageCollectorMXBean {
    @Override
    public long getCollectionCount() {
      return -1;
    }

    @Override
    public long getCollectionTime() {
      return -1;
    }
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
