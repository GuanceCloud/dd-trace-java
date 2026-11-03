package datadog.trace.instrumentation.disruptor;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;

import com.lmax.disruptor.RingBuffer;
import datadog.context.ContextScope;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.WeakMap;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.AgentSpanContext;
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

  public ContextScope start(
      RingBuffer<?> ring, long low, long high, ContextStore<Class, WeakMap> stores) {
    WeakMap<Object, AgentSpanContext> store = contexts(stores);
    AgentSpan span = startSpan("disruptor", "disruptor/publish");
    try {
      if (low <= high) {
        for (long sequence = low; ; sequence++) {
          Object event = ring.get(sequence);
          if (event != null) {
            store.put(event, span.spanContext());
          }
          if (sequence == high) {
            break;
          }
        }
      }
      return activateSpan(span);
    } catch (Throwable error) {
      span.finish();
      throw error;
    }
  }

  @SuppressWarnings("unchecked")
  public WeakMap<Object, AgentSpanContext> contexts(ContextStore<Class, WeakMap> stores) {
    // One shared map per Disruptor classloader, including separately injected consumer helpers.
    // Event slots use weak identity keys without the generic context-store's 50k entry limit.
    return stores.getOrCompute(RingBuffer.class, key -> WeakMap.Supplier.newWeakMap());
  }

  public ContextScope startBySpan(AgentSpanContext parent) {
    return activateSpan(startSpan("disruptor", "disruptor/consumer", parent));
  }
}
