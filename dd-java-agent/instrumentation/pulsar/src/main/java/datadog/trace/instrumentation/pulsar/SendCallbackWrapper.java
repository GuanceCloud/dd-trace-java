package datadog.trace.instrumentation.pulsar;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;

import datadog.context.ContextScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.impl.MessageImpl;
import org.apache.pulsar.client.impl.SendCallback;

public class SendCallbackWrapper implements SendCallback {

  private final AgentSpan span;
  private final SendCallback delegate;
  private final AtomicInteger completed = new AtomicInteger();

  public SendCallbackWrapper(AgentSpan span, SendCallback callback) {
    this.span = span;
    this.delegate = callback;
  }

  @Override
  public void sendComplete(Exception e) {
    try (ContextScope ignored = activateSpan(span)) {
      try {
        delegate.sendComplete(e);
      } finally {
        if (e != null) {
          span.addThrowable(e);
        }
      }
    } finally {
      complete(1);
    }
  }

  public void onSendError(Throwable error) {
    span.addThrowable(error);
    complete(1);
  }

  public void onSendExit() {
    complete(2);
  }

  private void complete(int part) {
    int previous;
    int updated;
    do {
      previous = completed.get();
      updated = previous | part;
      if (previous == updated) {
        return;
      }
    } while (!completed.compareAndSet(previous, updated));
    // A callback may run inline, before the send method has closed its scope.
    if (updated == 3) {
      span.finish();
    }
  }

  @Override
  public void addCallback(MessageImpl<?> msg, SendCallback scb) {
    this.delegate.addCallback(msg, scb);
  }

  @Override
  public SendCallback getNextSendCallback() {
    return this.delegate.getNextSendCallback();
  }

  @Override
  public MessageImpl<?> getNextMessage() {
    return this.delegate.getNextMessage();
  }

  @Override
  public CompletableFuture<MessageId> getFuture() {
    return this.delegate.getFuture();
  }
}
