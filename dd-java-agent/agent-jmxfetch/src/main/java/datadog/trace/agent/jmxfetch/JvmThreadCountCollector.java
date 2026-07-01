package datadog.trace.agent.jmxfetch;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

final class JvmThreadCountCollector {
  interface ThreadCountConsumer {
    void accept(boolean daemon, Thread.State state, long count);
  }

  private static final MethodHandle THREAD_INFO_IS_DAEMON = resolveThreadInfoIsDaemon();
  private static final ThreadMXBean THREAD_BEAN = ManagementFactory.getThreadMXBean();
  private static final Consumer<ThreadCountConsumer> THREAD_COUNT_COLLECTOR =
      chooseThreadCountCollector();

  private JvmThreadCountCollector() {}

  static void collect(ThreadCountConsumer consumer) {
    THREAD_COUNT_COLLECTOR.accept(consumer);
  }

  private static MethodHandle resolveThreadInfoIsDaemon() {
    try {
      return MethodHandles.publicLookup()
          .findVirtual(ThreadInfo.class, "isDaemon", MethodType.methodType(boolean.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      return null;
    }
  }

  private static Consumer<ThreadCountConsumer> chooseThreadCountCollector() {
    boolean isJava9OrNewer = THREAD_INFO_IS_DAEMON != null;
    boolean isNativeImage = System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    if (isJava9OrNewer && !isNativeImage) {
      return JvmThreadCountCollector::collectThreadCountsViaThreadMXBean;
    }
    return JvmThreadCountCollector::collectThreadCountsViaThreadGroup;
  }

  private static void collectThreadCountsViaThreadMXBean(ThreadCountConsumer consumer) {
    Map<Thread.State, long[]> daemonCounts = new EnumMap<>(Thread.State.class);
    Map<Thread.State, long[]> nonDaemonCounts = new EnumMap<>(Thread.State.class);
    long[] ids = THREAD_BEAN.getAllThreadIds();
    for (ThreadInfo info : THREAD_BEAN.getThreadInfo(ids)) {
      if (info == null) {
        continue;
      }
      Map<Thread.State, long[]> bucket = threadInfoIsDaemon(info) ? daemonCounts : nonDaemonCounts;
      bucket.computeIfAbsent(info.getThreadState(), k -> new long[1])[0]++;
    }
    recordThreadStateCounts(consumer, true, daemonCounts);
    recordThreadStateCounts(consumer, false, nonDaemonCounts);
  }

  private static void collectThreadCountsViaThreadGroup(ThreadCountConsumer consumer) {
    Map<Thread.State, long[]> daemonCounts = new EnumMap<>(Thread.State.class);
    Map<Thread.State, long[]> nonDaemonCounts = new EnumMap<>(Thread.State.class);
    for (Thread thread : enumerateAllThreads()) {
      Map<Thread.State, long[]> bucket = thread.isDaemon() ? daemonCounts : nonDaemonCounts;
      bucket.computeIfAbsent(thread.getState(), k -> new long[1])[0]++;
    }
    recordThreadStateCounts(consumer, true, daemonCounts);
    recordThreadStateCounts(consumer, false, nonDaemonCounts);
  }

  private static boolean threadInfoIsDaemon(ThreadInfo info) {
    try {
      return (boolean) THREAD_INFO_IS_DAEMON.invoke(info);
    } catch (Throwable t) {
      throw new IllegalStateException("Unexpected error invoking ThreadInfo#isDaemon()", t);
    }
  }

  private static Thread[] enumerateAllThreads() {
    ThreadGroup group = Thread.currentThread().getThreadGroup();
    while (group.getParent() != null) {
      group = group.getParent();
    }
    Thread[] buffer = new Thread[group.activeCount() + 10];
    int n = group.enumerate(buffer);
    if (n == buffer.length) {
      return buffer;
    }
    Thread[] trimmed = new Thread[n];
    System.arraycopy(buffer, 0, trimmed, 0, n);
    return trimmed;
  }

  private static void recordThreadStateCounts(
      ThreadCountConsumer consumer, boolean daemon, Map<Thread.State, long[]> counts) {
    for (Map.Entry<Thread.State, long[]> entry : counts.entrySet()) {
      consumer.accept(daemon, entry.getKey(), entry.getValue()[0]);
    }
  }
}
