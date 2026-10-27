package datadog.trace.instrumentation.disruptor;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.*;

import datadog.context.ContextScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;
import datadog.trace.bootstrap.instrumentation.decorator.BaseDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DisruptorDecorator extends BaseDecorator {
  public static final DisruptorDecorator DECORATE = new DisruptorDecorator();

  private static final Logger log = LoggerFactory.getLogger(DisruptorDecorator.class);
  public static final CharSequence DISRUPTOR = UTF8BytesString.create("disruptor");

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"disruptor"};
  }

  @Override
  protected CharSequence spanType() {
    return DISRUPTOR;
  }

  @Override
  protected CharSequence component() {
    return DISRUPTOR;
  }

  public ContextScope start() {
    AgentSpan span = startSpan("disruptor/publish");
    return activateSpan(span);
  }

  public ContextScope startBySpan(AgentSpan parent) {
    AgentSpan span = startSpan("disruptor/consumer", parent.context());
    return activateSpan(span);
  }
}
