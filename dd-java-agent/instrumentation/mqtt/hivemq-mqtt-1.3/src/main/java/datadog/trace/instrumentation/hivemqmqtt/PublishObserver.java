package datadog.trace.instrumentation.hivemqmqtt;

import datadog.context.ContextScope;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;

/** Preserves the original cancellation path while observing a single publish completion. */
public final class PublishObserver implements SingleObserver<Object>, Disposable {
  private final SingleObserver<Object> delegate;
  private final PublishState state;
  private Disposable upstream;

  public PublishObserver(SingleObserver<Object> delegate, PublishState state) {
    this.delegate = delegate;
    this.state = state;
  }

  @Override
  public void onSubscribe(Disposable disposable) {
    upstream = disposable;
    delegate.onSubscribe(this);
  }

  @Override
  public void onSuccess(Object result) {
    try (ContextScope scope = state.activate()) {
      delegate.onSuccess(result);
    } catch (Throwable error) {
      state.finish(error);
      throw error;
    } finally {
      state.finish(null);
    }
  }

  @Override
  public void onError(Throwable error) {
    try (ContextScope scope = state.activate()) {
      delegate.onError(error);
    } finally {
      state.finish(error);
    }
  }

  @Override
  public void dispose() {
    try {
      upstream.dispose();
    } finally {
      state.finish(null);
    }
  }

  @Override
  public boolean isDisposed() {
    return upstream.isDisposed();
  }
}
