package test.disruptor;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;

import com.lmax.disruptor.EventHandler;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;

public class TypedHandler implements EventHandler<EqualEvent> {
  public AgentSpan observed;

  @Override
  public void onEvent(EqualEvent event, long sequence, boolean endOfBatch) {
    observed = activeSpan();
  }
}
