package datadog.trace.agent.jmxfetch;

import static datadog.trace.util.AgentThreadFactory.AgentThread.JMX_COLLECTOR;
import static datadog.trace.util.AgentThreadFactory.newAgentThread;

import datadog.metrics.api.statsd.StatsDClient;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

final class JvmGcStatsDReporter implements Runnable {
  static final String COLLECTION_COUNT_METRIC = "jvm.gc.collection_count";
  static final String COLLECTION_TIME_METRIC = "jvm.gc.collection_time";

  private static final long MIN_CHECK_PERIOD_MILLIS = 1_000L;
  private static final String GC_TAG = "gc:";

  private final StatsDClient statsd;
  private final String[] commonTags;
  private final BooleanSupplier shouldCollect;
  private final long checkPeriodMillis;
  private final List<GarbageCollectorMXBean> gcBeans;

  JvmGcStatsDReporter(
      StatsDClient statsd,
      Map<String, String> commonTags,
      long checkPeriodMillis,
      BooleanSupplier shouldCollect) {
    this(
        statsd,
        commonTags,
        checkPeriodMillis,
        shouldCollect,
        ManagementFactory.getGarbageCollectorMXBeans());
  }

  JvmGcStatsDReporter(
      StatsDClient statsd,
      Map<String, String> commonTags,
      long checkPeriodMillis,
      BooleanSupplier shouldCollect,
      List<GarbageCollectorMXBean> gcBeans) {
    this.statsd = statsd;
    this.commonTags = toTagArray(commonTags);
    this.checkPeriodMillis = Math.max(MIN_CHECK_PERIOD_MILLIS, checkPeriodMillis);
    this.shouldCollect = shouldCollect;
    this.gcBeans = gcBeans;
  }

  static void start(
      StatsDClient statsd,
      Map<String, String> commonTags,
      long checkPeriodMillis,
      BooleanSupplier shouldCollect) {
    Thread thread =
        newAgentThread(
            JMX_COLLECTOR,
            "-gc",
            new JvmGcStatsDReporter(statsd, commonTags, checkPeriodMillis, shouldCollect),
            true);
    thread.start();
  }

  @Override
  public void run() {
    while (!Thread.currentThread().isInterrupted()) {
      if (shouldCollect.getAsBoolean()) {
        reportOnce();
      }
      try {
        Thread.sleep(checkPeriodMillis);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  void reportOnce() {
    for (GarbageCollectorMXBean gcBean : gcBeans) {
      String[] tags = tagsFor(gcBean.getName());
      long collectionCount = gcBean.getCollectionCount();
      if (collectionCount >= 0) {
        statsd.gauge(COLLECTION_COUNT_METRIC, collectionCount, tags);
      }
      long collectionTime = gcBean.getCollectionTime();
      if (collectionTime >= 0) {
        statsd.gauge(COLLECTION_TIME_METRIC, collectionTime, tags);
      }
    }
  }

  private String[] tagsFor(String gcName) {
    String[] tags = Arrays.copyOf(commonTags, commonTags.length + 1);
    tags[commonTags.length] = GC_TAG + gcName;
    return tags;
  }

  private static String[] toTagArray(Map<String, String> commonTags) {
    return commonTags.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> entry.getKey() + ":" + entry.getValue())
        .toArray(String[]::new);
  }
}
