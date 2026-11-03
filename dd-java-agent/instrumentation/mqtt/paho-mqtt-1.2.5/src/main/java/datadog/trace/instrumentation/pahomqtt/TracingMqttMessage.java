package datadog.trace.instrumentation.pahomqtt;

import datadog.trace.instrumentation.mqtt.payload.MqttPayload;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.eclipse.paho.client.mqttv3.MqttMessage;

/** Copies metadata while keeping the caller's message and payload unchanged. */
public class TracingMqttMessage extends MqttMessage {
  private final boolean wireEnvelope;

  public TracingMqttMessage(MqttMessage original, byte[] payload, boolean wireEnvelope) {
    super(payload);
    this.wireEnvelope = wireEnvelope;
    setQos(original.getQos());
    setRetained(original.isRetained());
    setDuplicate(original.isDuplicate());
    setId(original.getId());
  }

  public static MqttPayload.Decoded decode(MqttMessage message) {
    return MqttPayload.ENABLED && message != null
        ? MqttPayload.decode(ByteBuffer.wrap(message.getPayload()))
        : null;
  }

  public static MqttMessage unwrap(MqttMessage message, MqttPayload.Decoded decoded) {
    if (decoded != null) {
      byte[] payload = message.getPayload();
      return new TracingMqttMessage(
          message, Arrays.copyOfRange(payload, decoded.payloadOffset, payload.length), false);
    }
    return message;
  }

  public static MqttMessage forToken(MqttMessage message, PublishState state) {
    if (state != null) {
      state.restoreMessageId();
      return state.original;
    }
    if (message instanceof TracingMqttMessage && ((TracingMqttMessage) message).wireEnvelope) {
      return unwrap(message, decode(message));
    }
    return message;
  }
}
