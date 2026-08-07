package datadog.trace.instrumentation.springamqp;

import static datadog.context.Context.root;
import static datadog.trace.bootstrap.instrumentation.api.AgentSpan.fromContext;

import datadog.context.Context;
import datadog.context.propagation.Propagators;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.AgentSpanContext;
import datadog.trace.bootstrap.instrumentation.api.ContextVisitors;
import java.util.Map;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

public final class SpringRabbitMessageContextHelper {

  private SpringRabbitMessageContextHelper() {}

  public static AgentSpanContext extractParent(Message message) {
    if (message == null) {
      return null;
    }
    MessageProperties properties = message.getMessageProperties();
    if (properties == null) {
      return null;
    }
    Map<String, Object> headers = properties.getHeaders();
    if (headers == null || headers.isEmpty()) {
      return null;
    }
    Context extracted =
        Propagators.defaultPropagator().extract(root(), headers, ContextVisitors.objectValuesMap());
    AgentSpan extractedSpan = fromContext(extracted);
    return extractedSpan == null ? null : extractedSpan.spanContext();
  }
}
