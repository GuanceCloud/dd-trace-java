package datadog.trace.instrumentation.ons_client;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.ons_client.ProducerDecorator.PRODUCER_DECORATOR;

import com.aliyun.openservices.ons.api.OnExceptionContext;
import com.aliyun.openservices.ons.api.SendCallback;
import com.aliyun.openservices.ons.api.SendResult;
import datadog.context.ContextScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.concurrent.atomic.AtomicInteger;

public class WrappingSendCallback implements SendCallback {
  private final SendCallback callback;
  private final AgentSpan span;
  private final AtomicInteger completed = new AtomicInteger();

  public WrappingSendCallback(SendCallback callback, AgentSpan span) {
    this.callback = callback;
    this.span = span;
  }

  @Override
  public void onSuccess(SendResult sendResult) {
    try (ContextScope ignored = activateSpan(span)) {
      span.setTag("sendAsync_status", "success");
      try {
        callback.onSuccess(sendResult);
      } finally {
        PRODUCER_DECORATOR.beforeFinish(span);
      }
    } finally {
      complete(1);
    }
  }

  @Override
  public void onException(OnExceptionContext context) {
    try (ContextScope ignored = activateSpan(span)) {
      span.setTag("sendAsync_status", "exception");
      try {
        callback.onException(context);
      } finally {
        PRODUCER_DECORATOR.onError(span, context.getException());
        PRODUCER_DECORATOR.beforeFinish(span);
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
}
