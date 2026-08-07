package datadog.trace.common.writer.ddagent;

import static datadog.trace.api.sampling.PrioritySampling.SAMPLER_DROP;
import static datadog.trace.api.sampling.PrioritySampling.USER_DROP;

import datadog.communication.ddagent.DroppingPolicy;
import datadog.trace.api.Config;
import datadog.trace.core.CoreSpan;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum Prioritization {
  ENSURE_TRACE {
    @Override
    public PrioritizationStrategy create(
        final Queue<Object> primary,
        final Queue<Object> secondary,
        final Queue<Object> spanSampling,
        DroppingPolicy neverUsed) {
      return new EnsureTraceStrategy(primary, secondary, spanSampling);
    }
  },
  FAST_LANE {
    @Override
    public PrioritizationStrategy create(
        final Queue<Object> primary,
        final Queue<Object> secondary,
        final Queue<Object> spanSampling,
        DroppingPolicy droppingPolicy) {
      return new FastLaneStrategy(primary, secondary, spanSampling, droppingPolicy);
    }
  };

  public abstract PrioritizationStrategy create(
      Queue<Object> primary,
      Queue<Object> secondary,
      Queue<Object> spanSampling,
      DroppingPolicy droppingPolicy);

  private static final Logger log = LoggerFactory.getLogger(Prioritization.class);

  private static <T extends CoreSpan<T>> PrioritizationStrategy.PublishResult offerOrLogOverflow(
      String queueName, Queue<Object> queue, T root, int priority, List<T> trace) {
    if (queue.offer(trace)) {
      return PrioritizationStrategy.PublishResult.ENQUEUED_FOR_SERIALIZATION;
    }
    logBufferOverflow(queueName, queue, root, priority, trace);
    return PrioritizationStrategy.PublishResult.DROPPED_BUFFER_OVERFLOW;
  }

  private static <T extends CoreSpan<T>> PrioritizationStrategy.PublishResult offerOrLogSpanSamplingOverflow(
      Queue<Object> queue, T root, int priority, List<T> trace) {
    if (queue.offer(trace)) {
      return PrioritizationStrategy.PublishResult.ENQUEUED_FOR_SINGLE_SPAN_SAMPLING;
    }
    logBufferOverflow("spanSampling", queue, root, priority, trace);
    return PrioritizationStrategy.PublishResult.DROPPED_BUFFER_OVERFLOW;
  }

  private static <T extends CoreSpan<T>> void logBufferOverflow(
      String queueName, Queue<Object> queue, T root, int priority, List<T> trace) {
    Config config = Config.get();
    if (!config.isDebugEnabled()) {
      return;
    }
    int remainingCapacity = -1;
    if (queue instanceof BlockingQueue) {
      remainingCapacity = ((BlockingQueue<?>) queue).remainingCapacity();
    }
    log.debug(
        "Trace buffer overflow on {} queue: traceBufferSize={}, queueSize={}, remainingCapacity={}, traceSize={}, priority={}, forceKeep={}",
        queueName,
        config.getTraceBufferSize(),
        queue.size(),
        remainingCapacity,
        trace.size(),
        priority,
        root.isForceKeep());
  }

  private abstract static class PrioritizationStrategyWithFlush implements PrioritizationStrategy {

    protected final Queue<Object> primary;

    protected PrioritizationStrategyWithFlush(Queue<Object> primary) {
      this.primary = primary;
    }

    @Override
    public boolean flush(final long timeout, final TimeUnit timeUnit) {
      // ok not to flush the secondary
      final CountDownLatch latch = new CountDownLatch(1);
      final FlushEvent event = new FlushEvent(latch);
      blockingOffer(primary, event);
      try {
        return latch.await(timeout, timeUnit);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }

    protected void blockingOffer(final Queue<Object> queue, final Object event) {
      boolean offered;
      do {
        offered = queue.offer(event);
      } while (!offered);
    }
  }

  private static final class EnsureTraceStrategy extends PrioritizationStrategyWithFlush {

    private final Queue<Object> secondary;
    private final Queue<Object> spanSampling;

    private EnsureTraceStrategy(
        final Queue<Object> primary,
        final Queue<Object> secondary,
        final Queue<Object> spanSampling) {
      super(primary);
      this.secondary = secondary;
      this.spanSampling = spanSampling;
    }

    @Override
    public <T extends CoreSpan<T>> PublishResult publish(
        T root, int priority, final List<T> trace) {
      switch (priority) {
        case SAMPLER_DROP:
        case USER_DROP:
          if (spanSampling != null) {
            // send dropped traces for single span sampling
            return offerOrLogSpanSamplingOverflow(spanSampling, root, priority, trace);
          }
          return offerOrLogOverflow("secondary", secondary, root, priority, trace);
        default:
          blockingOffer(primary, trace);
          return PublishResult.ENQUEUED_FOR_SERIALIZATION;
      }
    }
  }

  private static final class FastLaneStrategy extends PrioritizationStrategyWithFlush {

    private final Queue<Object> secondary;
    private final Queue<Object> spanSampling;
    private final DroppingPolicy droppingPolicy;

    private FastLaneStrategy(
        final Queue<Object> primary,
        final Queue<Object> secondary,
        final Queue<Object> spanSampling,
        DroppingPolicy droppingPolicy) {
      super(primary);
      this.secondary = secondary;
      this.spanSampling = spanSampling;
      this.droppingPolicy = droppingPolicy;
    }

    @Override
    public <T extends CoreSpan<T>> PublishResult publish(T root, int priority, List<T> trace) {
      if (root.isForceKeep()) {
        return offerOrLogOverflow("primary", primary, root, priority, trace);
      }
      switch (priority) {
        case SAMPLER_DROP:
        case USER_DROP:
          if (spanSampling != null) {
            // send dropped traces for single span sampling
            return offerOrLogSpanSamplingOverflow(spanSampling, root, priority, trace);
          }
          if (droppingPolicy.active()) {
            return PublishResult.DROPPED_BY_POLICY;
          }
          return offerOrLogOverflow("secondary", secondary, root, priority, trace);
        default:
          return offerOrLogOverflow("primary", primary, root, priority, trace);
      }
    }
  }
}
