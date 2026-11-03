package test.disruptor;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;

import com.lmax.disruptor.EventHandler;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;

public class RecordingHandler implements EventHandler<Object> {
  public volatile AgentSpan observed;
  public java.util.concurrent.CountDownLatch done;
  public RuntimeException failure;
  public int calls;

  @Override
  public void onEvent(Object event, long sequence, boolean endOfBatch) {
    observed = activeSpan();
    calls++;
    if (failure != null) throw failure;
    if (done != null) done.countDown();
  }
}
