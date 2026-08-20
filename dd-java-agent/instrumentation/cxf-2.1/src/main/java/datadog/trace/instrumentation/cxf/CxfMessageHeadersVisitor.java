package datadog.trace.instrumentation.cxf;

import datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import java.lang.reflect.Array;
import java.util.Map;
import org.apache.cxf.message.Message;

public final class CxfMessageHeadersVisitor implements AgentPropagation.ContextVisitor<Message> {
  public static final CxfMessageHeadersVisitor INSTANCE = new CxfMessageHeadersVisitor();

  private CxfMessageHeadersVisitor() {}

  @Override
  public void forEachKey(Message carrier, AgentPropagation.KeyClassifier classifier) {
    final Object protocolHeaders = carrier.get(Message.PROTOCOL_HEADERS);
    if (!(protocolHeaders instanceof Map)) {
      return;
    }

    for (Map.Entry<?, ?> entry : ((Map<?, ?>) protocolHeaders).entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) {
        continue;
      }
      final String key = entry.getKey().toString();
      final Object value = entry.getValue();
      if (value instanceof Iterable) {
        for (Object item : (Iterable<?>) value) {
          if (item != null && !classifier.accept(key, item.toString())) {
            return;
          }
        }
      } else if (value.getClass().isArray()) {
        final int length = Array.getLength(value);
        for (int i = 0; i < length; i++) {
          final Object item = Array.get(value, i);
          if (item != null && !classifier.accept(key, item.toString())) {
            return;
          }
        }
      } else if (!classifier.accept(key, value.toString())) {
        return;
      }
    }
  }
}
