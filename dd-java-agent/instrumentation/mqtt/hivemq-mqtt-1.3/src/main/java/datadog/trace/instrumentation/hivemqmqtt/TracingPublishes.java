package datadog.trace.instrumentation.hivemqmqtt;

import com.hivemq.client.mqtt.MqttClientConfig;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.rx.FlowableWithSingle;
import com.hivemq.client.rx.reactivestreams.WithSingleSubscriber;
import io.reactivex.Flowable;
import org.reactivestreams.Subscriber;

/** Keeps HiveMQ's additional subscription acknowledgement signal intact. */
public final class TracingPublishes extends FlowableWithSingle<Mqtt3Publish, Object> {
  private final Flowable<Mqtt3Publish> source;
  private final MqttClientConfig config;

  public TracingPublishes(Flowable<Mqtt3Publish> source, MqttClientConfig config) {
    this.source = source;
    this.config = config;
  }

  @Override
  protected void subscribeActual(Subscriber<? super Mqtt3Publish> subscriber) {
    source.subscribe(new ConsumeSubscriber(subscriber, config));
  }

  @Override
  protected void subscribeBothActual(
      WithSingleSubscriber<? super Mqtt3Publish, ? super Object> subscriber) {
    ((FlowableWithSingle<Mqtt3Publish, Object>) source)
        .subscribeBoth(new ConsumeSubscriber(subscriber, config));
  }
}
