package datadog.trace.instrumentation.hivemqmqtt;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.hivemqmqtt.HiveMqttDecorator.CONSUMER;

import com.hivemq.client.mqtt.MqttClientConfig;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import datadog.context.ContextScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.function.Consumer;

/** Starts a fresh consumer trace on the actual application callback thread. */
public final class ConsumeCallback implements Consumer<Mqtt3Publish> {
  private final Consumer<Mqtt3Publish> delegate;
  private final MqttClientConfig config;

  public ConsumeCallback(Consumer<Mqtt3Publish> delegate, MqttClientConfig config) {
    this.delegate = delegate;
    this.config = config;
  }

  @Override
  public void accept(Mqtt3Publish publish) {
    AgentSpan span = CONSUMER.start(config, publish);
    try (ContextScope scope = activateSpan(span)) {
      try {
        delegate.accept(publish);
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
}
