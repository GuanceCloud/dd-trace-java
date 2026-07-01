package datadog.trace.agent.jmxfetch;

import static datadog.trace.util.AgentThreadFactory.AgentThread.JMX_COLLECTOR;
import static datadog.trace.util.AgentThreadFactory.newAgentThread;

import datadog.metrics.api.statsd.StatsDClient;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

final class JvmThreadCountStatsDReporter implements Runnable {
  static final String METRIC_NAME = "jvm.thread.count";

  private static final long MIN_CHECK_PERIOD_MILLIS = 1_000L;
  private static final String THREAD_DAEMON_TAG = "jvm.thread.daemon:";
  private static final String THREAD_STATE_TAG = "jvm.thread.state:";

  private final StatsDClient statsd;
  private final String[] commonTags;
  private final BooleanSupplier shouldCollect;
  private final long checkPeriodMillis;

  JvmThreadCountStatsDReporter(
      StatsDClient statsd,
      Map<String, String> commonTags,
      long checkPeriodMillis,
      BooleanSupplier shouldCollect) {
    this.statsd = statsd;
    this.commonTags = toTagArray(commonTags);
    this.checkPeriodMillis = Math.max(MIN_CHECK_PERIOD_MILLIS, checkPeriodMillis);
    this.shouldCollect = shouldCollect;
  }

  static void start(
      StatsDClient statsd,
      Map<String, String> commonTags,
      long checkPeriodMillis,
      BooleanSupplier shouldCollect) {
    Thread thread =
        newAgentThread(
            JMX_COLLECTOR,
            "-thread-count",
            new JvmThreadCountStatsDReporter(
                statsd, commonTags, checkPeriodMillis, shouldCollect),
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
    JvmThreadCountCollector.collect(
        (daemon, state, count) -> statsd.gauge(METRIC_NAME, count, tagsFor(daemon, state)));
  }

  private String[] tagsFor(boolean daemon, Thread.State state) {
    String[] tags = Arrays.copyOf(commonTags, commonTags.length + 2);
    tags[commonTags.length] = THREAD_DAEMON_TAG + daemon;
    tags[commonTags.length + 1] = THREAD_STATE_TAG + state.name().toLowerCase(Locale.ROOT);
    return tags;
  }

  private static String[] toTagArray(Map<String, String> commonTags) {
    return commonTags.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> entry.getKey() + ":" + entry.getValue())
        .toArray(String[]::new);
  }
}
