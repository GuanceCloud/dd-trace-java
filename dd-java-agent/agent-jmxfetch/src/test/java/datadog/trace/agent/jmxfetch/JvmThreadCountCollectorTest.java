package datadog.trace.agent.jmxfetch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JvmThreadCountCollectorTest {

  @Test
  void collectReportsPositiveBucketsForDaemonAndState() {
    List<Bucket> buckets = new ArrayList<>();

    JvmThreadCountCollector.collect(
        (daemon, state, count) -> buckets.add(new Bucket(daemon, state, count)));

    assertFalse(buckets.isEmpty(), "Expected at least one thread count bucket");

    Set<Thread.State> expectedStates = EnumSet.allOf(Thread.State.class);
    boolean sawDaemon = false;
    boolean sawNonDaemon = false;
    for (Bucket bucket : buckets) {
      assertTrue(bucket.count > 0, "Buckets should skip zero counts");
      assertTrue(expectedStates.contains(bucket.state), "Unexpected thread state: " + bucket.state);
      sawDaemon |= bucket.daemon;
      sawNonDaemon |= !bucket.daemon;
    }

    assertTrue(sawDaemon, "Expected at least one daemon bucket");
    assertTrue(sawNonDaemon, "Expected at least one non-daemon bucket");
  }

  private static final class Bucket {
    private final boolean daemon;
    private final Thread.State state;
    private final long count;

    private Bucket(boolean daemon, Thread.State state, long count) {
      this.daemon = daemon;
      this.state = state;
      this.count = count;
    }
  }
}
