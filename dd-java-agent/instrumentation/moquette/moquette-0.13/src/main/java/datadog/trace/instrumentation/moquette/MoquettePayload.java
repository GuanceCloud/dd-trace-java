package datadog.trace.instrumentation.moquette;

import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.mqtt.payload.MqttPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import java.nio.ByteBuffer;

/** Transfers broker trace context on the wire while callback observers see the business payload. */
public final class MoquettePayload {
  private MoquettePayload() {}

  public static MqttPayload.Decoded decode(MqttPublishMessage message) {
    return MqttPayload.ENABLED ? MqttPayload.decode(message.payload().nioBuffer()) : null;
  }

  public static MqttPublishMessage reframe(
      AgentSpan span, MqttPublishMessage message, boolean internal, MqttPayload.Decoded decoded) {
    if (!MqttPayload.ENABLED) {
      return null;
    }
    ByteBuffer source = message.payload().nioBuffer();
    if (!internal && decoded == null) {
      return null;
    }
    int bodyLength = decoded == null ? source.remaining() : decoded.payloadLength;
    if (message.fixedHeader().isRetain() && bodyLength == 0) {
      if (decoded == null) {
        return null;
      }
      return new MqttPublishMessage(
          message.fixedHeader(), message.variableHeader(), Unpooled.EMPTY_BUFFER);
    }
    byte[] framed =
        internal ? MqttPayload.encode(span, source) : MqttPayload.reframe(span, source, decoded);
    return framed == null
        ? null
        : new MqttPublishMessage(
            message.fixedHeader(), message.variableHeader(), Unpooled.wrappedBuffer(framed));
  }

  public static MqttPublishMessage forCallback(MqttPublishMessage message) {
    if (MqttPayload.ENABLED) {
      ByteBuf payload = message.payload();
      MqttPayload.Decoded decoded = MqttPayload.decode(payload.nioBuffer());
      if (decoded != null) {
        ByteBuf view =
            payload.slice(payload.readerIndex() + decoded.payloadOffset, decoded.payloadLength);
        return new MqttPublishMessage(message.fixedHeader(), message.variableHeader(), view);
      }
    }
    return message;
  }
}
