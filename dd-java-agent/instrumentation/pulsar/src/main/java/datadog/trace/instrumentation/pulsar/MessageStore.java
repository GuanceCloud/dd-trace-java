package datadog.trace.instrumentation.pulsar;

import datadog.context.ContextScope;
import java.util.HashMap;
import java.util.Map;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.impl.TopicMessageImpl;

public class MessageStore {
  private static final Map<Message<?>, ContextScope> MSG_FIELD =
      new HashMap<Message<?>, ContextScope>();

  public static void Inject(Message<?> instance, ContextScope scope) {
    if (instance instanceof TopicMessageImpl<?>) {
      TopicMessageImpl<?> topicMessage = (TopicMessageImpl<?>) instance;
      instance = topicMessage.getMessage();
    }
    if (instance != null) {
      MSG_FIELD.put(instance, scope);
    }
  }

  public static ContextScope extract(Message<?> instance) {
    if (instance instanceof TopicMessageImpl<?>) {
      TopicMessageImpl<?> topicMessage = (TopicMessageImpl<?>) instance;
      instance = topicMessage.getMessage();
    }
    if (instance == null) {
      return null;
    }
    ContextScope as = MSG_FIELD.get(instance);
    MSG_FIELD.remove(instance);
    return as;
  }
}
