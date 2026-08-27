package rabbitbatch;

import datadog.trace.api.CorrelationIdentifier;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.amqp.core.BatchMessageListener;
import org.springframework.amqp.core.Message;

public class BatchReceiver implements BatchMessageListener {

  public final AtomicInteger received = new AtomicInteger();
  public final AtomicInteger batches = new AtomicInteger();
  public final AtomicReference<String> traceId = new AtomicReference<>();
  public final AtomicReference<String> spanId = new AtomicReference<>();

  @Override
  public void onMessageBatch(List<Message> messages) {
    batches.incrementAndGet();
    traceId.set(CorrelationIdentifier.getTraceId());
    spanId.set(CorrelationIdentifier.getSpanId());
    received.addAndGet(messages.size());
  }
}
