package datadog.trace.instrumentation.hivemqmqtt;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.hivemqmqtt.HiveMqttDecorator.CONSUMER;

import com.hivemq.client.mqtt.MqttClientConfig;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.rx.FlowableWithSingleSubscriber;
import com.hivemq.client.rx.reactivestreams.WithSingleSubscriber;
import datadog.context.ContextScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/** Preserves subscription, acknowledgements and demand while tracing incoming reactive signals. */
public final class ConsumeSubscriber implements FlowableWithSingleSubscriber<Mqtt3Publish, Object> {
  private final Subscriber<? super Mqtt3Publish> delegate;
  private final MqttClientConfig config;

  public ConsumeSubscriber(Subscriber<? super Mqtt3Publish> delegate, MqttClientConfig config) {
    this.delegate = delegate;
    this.config = config;
  }

  @Override
  public void onSubscribe(Subscription subscription) {
    delegate.onSubscribe(subscription);
  }

  @Override
  public void onNext(Mqtt3Publish publish) {
    AgentSpan span = CONSUMER.start(config, publish);
    try (ContextScope scope = activateSpan(span)) {
      try {
        delegate.onNext(publish);
      } catch (Throwable error) {
        CONSUMER.onError(span, error);
        throw error;
      } finally {
        CONSUMER.beforeFinish(span);
      }
    } finally {
      span.finish();
    }
  }

  @Override
  public void onSingle(Object value) {
    ((WithSingleSubscriber<?, Object>) delegate).onSingle(value);
  }

  @Override
  public void onError(Throwable error) {
    delegate.onError(error);
  }

  @Override
  public void onComplete() {
    delegate.onComplete();
  }
}
