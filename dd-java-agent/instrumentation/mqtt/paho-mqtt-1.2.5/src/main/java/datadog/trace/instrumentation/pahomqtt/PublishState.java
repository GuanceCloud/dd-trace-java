package datadog.trace.instrumentation.pahomqtt;

import static datadog.trace.instrumentation.pahomqtt.PahoMqttDecorator.PRODUCER;

import datadog.context.Context;
import datadog.context.ContextKey;
import datadog.context.ContextScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.mqtt.payload.MqttPayload;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * Owns a publish span until Paho processes its delivery completion, independently of user
 * listeners.
 */
public class PublishState {
  private static final ContextKey<PublishState> KEY = ContextKey.named("paho-mqtt-publish");
  public final AgentSpan span;
  public final MqttMessage original;
  public final MqttMessage outgoing;
  private final AtomicBoolean finished = new AtomicBoolean();

  public PublishState(MqttAsyncClient client, String topic, MqttMessage message) {
    original = message;
    span = PRODUCER.start(topic, message, client.getClientId(), client.getServerURI());
    byte[] framed = null;
    if (MqttPayload.ENABLED && !(message.isRetained() && message.getPayload().length == 0)) {
      framed = MqttPayload.encode(span, ByteBuffer.wrap(message.getPayload()));
    }
    outgoing = framed == null ? message : new TracingMqttMessage(message, framed, true);
  }

  public ContextScope activatePublish() {
    return Context.current().with(span).with(KEY, this).attach();
  }

  public static PublishState current() {
    return Context.current().get(KEY);
  }

  public boolean isFinished() {
    return finished.get();
  }

  public void finish(Throwable error) {
    if (finished.compareAndSet(false, true)) {
      PRODUCER.finish(span, error);
    }
  }

  public void restoreMessageId() {
    if (outgoing != original) {
      original.setId(outgoing.getId());
    }
  }
}
