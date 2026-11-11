package datadog.trace.instrumentation.hivemqmqtt;

import static datadog.trace.instrumentation.hivemqmqtt.HiveMqttDecorator.PRODUCER;

import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult;
import datadog.context.ContextScope;
import io.reactivex.FlowableSubscriber;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/** Observes each reactive publish without changing demand or cancellation. */
public final class PublishSubscriber implements FlowableSubscriber<Object>, Subscription {
  private final Subscriber<Object> delegate;
  private final PublishState state;
  private Subscription upstream;

  public PublishSubscriber(Subscriber<Object> delegate, PublishState state) {
    this.delegate = delegate;
    this.state = state;
  }

  @Override
  public void onSubscribe(Subscription subscription) {
    upstream = subscription;
    delegate.onSubscribe(this);
  }

  @Override
  public void onNext(Object result) {
    if (result instanceof Mqtt5PublishResult) {
      PRODUCER.onError(state.span, ((Mqtt5PublishResult) result).getError().orElse(null));
    }
    try (ContextScope scope = state.activate()) {
      delegate.onNext(result);
    } catch (Throwable error) {
      state.finish(error);
      throw error;
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
  public void onComplete() {
    try (ContextScope scope = state.activate()) {
      delegate.onComplete();
    } finally {
      state.finish(null);
    }
  }

  @Override
  public void request(long n) {
    upstream.request(n);
  }

  @Override
  public void cancel() {
    try {
      upstream.cancel();
    } finally {
      state.finish(null);
    }
  }
}
