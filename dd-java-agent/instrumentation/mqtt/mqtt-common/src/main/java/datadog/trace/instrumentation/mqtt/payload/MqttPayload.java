package datadog.trace.instrumentation.mqtt.payload;

import static datadog.context.propagation.Propagators.defaultPropagator;
import static datadog.trace.bootstrap.instrumentation.api.AgentPropagation.extractContextAndGetSpanContext;
import static datadog.trace.bootstrap.instrumentation.api.ContextVisitors.stringValuesMap;
import static java.nio.charset.StandardCharsets.UTF_8;

import datadog.context.propagation.CarrierSetter;
import datadog.trace.bootstrap.config.provider.ConfigProvider;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.AgentSpanContext;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

/** Opt-in binary MQTT 3 payload framing shared by clients and brokers. */
public final class MqttPayload {
  public static final boolean ENABLED =
      ConfigProvider.getInstance().getBoolean("trace.mqtt.payload.propagation.enabled", false);
  private static final long MAGIC_HIGH = 0x44444d5154545452L;
  private static final long MAGIC_LOW = 0x4143450001000d0aL;
  private static final int PREFIX_SIZE = 20;
  private static final int MAX_HEADERS = 64;
  private static final int MAX_HEADER_BYTES = 8192;

  private MqttPayload() {}

  public static byte[] encode(AgentSpan span, ByteBuffer payload) {
    return encode(headers(span), payload);
  }

  public static byte[] reframe(AgentSpan span, ByteBuffer payload, Decoded decoded) {
    return encode(headers(span), body(payload, decoded));
  }

  private static Map<String, String> headers(AgentSpan span) {
    Map<String, String> headers = new LinkedHashMap<>();
    defaultPropagator().inject(span, headers, HeadersSetter.INSTANCE);
    return headers;
  }

  static byte[] reframe(Map<String, String> headers, ByteBuffer payload) {
    return encode(headers, body(payload, decode(payload)));
  }

  private static ByteBuffer body(ByteBuffer payload, Decoded decoded) {
    ByteBuffer body = payload.duplicate();
    if (decoded != null) {
      body.position(body.position() + decoded.payloadOffset);
    }
    return body;
  }

  static byte[] encode(Map<String, String> headers, ByteBuffer payload) {
    if (headers.isEmpty() || headers.size() > MAX_HEADERS) {
      return null;
    }
    ByteArrayOutputStream header = new ByteArrayOutputStream(256);
    writeShort(header, headers.size());
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      if (entry.getKey() == null
          || entry.getValue() == null
          || entry.getKey().length() > 256
          || entry.getValue().length() > MAX_HEADER_BYTES) {
        return null;
      }
      byte[] key = entry.getKey().getBytes(UTF_8);
      byte[] value = entry.getValue().getBytes(UTF_8);
      if (key.length == 0
          || key.length > 256
          || value.length == 0
          || header.size() + 4 + key.length + value.length > MAX_HEADER_BYTES) {
        return null;
      }
      writeShort(header, key.length);
      header.write(key, 0, key.length);
      writeShort(header, value.length);
      header.write(value, 0, value.length);
    }
    ByteBuffer body = payload.duplicate();
    byte[] headerBytes = header.toByteArray();
    if (body.remaining() > Integer.MAX_VALUE - PREFIX_SIZE - headerBytes.length) {
      return null;
    }
    byte[] framed = new byte[PREFIX_SIZE + headerBytes.length + body.remaining()];
    ByteBuffer output = ByteBuffer.wrap(framed).order(ByteOrder.BIG_ENDIAN);
    output.putLong(MAGIC_HIGH).putLong(MAGIC_LOW).putInt(headerBytes.length);
    output.put(headerBytes).put(body);
    return framed;
  }

  private static void writeShort(ByteArrayOutputStream output, int value) {
    output.write(value >>> 8);
    output.write(value);
  }

  public static Decoded decode(ByteBuffer payload) {
    if (payload.remaining() < PREFIX_SIZE + 2) {
      return null;
    }
    ByteBuffer input = payload.duplicate().order(ByteOrder.BIG_ENDIAN);
    if (input.getLong() != MAGIC_HIGH || input.getLong() != MAGIC_LOW) {
      return null;
    }
    int headerSize = input.getInt();
    if (headerSize < 2 || headerSize > MAX_HEADER_BYTES || headerSize > input.remaining()) {
      return null;
    }
    ByteBuffer header = input.slice().order(ByteOrder.BIG_ENDIAN);
    header.limit(headerSize);
    int count = header.getShort() & 0xffff;
    if (count == 0 || count > MAX_HEADERS) {
      return null;
    }
    Map<String, String> headers = new LinkedHashMap<>();
    for (int i = 0; i < count; i++) {
      String key = readString(header, 256);
      String value = readString(header, MAX_HEADER_BYTES);
      if (key == null || value == null || headers.put(key, value) != null) {
        return null;
      }
    }
    if (header.hasRemaining()) {
      return null;
    }
    return new Decoded(
        headers, PREFIX_SIZE + headerSize, payload.remaining() - PREFIX_SIZE - headerSize);
  }

  private static String readString(ByteBuffer input, int maximum) {
    if (input.remaining() < 2) {
      return null;
    }
    int size = input.getShort() & 0xffff;
    if (size == 0 || size > maximum || size > input.remaining()) {
      return null;
    }
    byte[] bytes = new byte[size];
    input.get(bytes);
    return new String(bytes, UTF_8);
  }

  public static final class Decoded {
    private final Map<String, String> headers;
    public final int payloadOffset;
    public final int payloadLength;

    public Decoded(Map<String, String> headers, int payloadOffset, int payloadLength) {
      this.headers = headers;
      this.payloadOffset = payloadOffset;
      this.payloadLength = payloadLength;
    }

    public AgentSpanContext parent() {
      return extractContextAndGetSpanContext(headers, stringValuesMap());
    }
  }

  public static final class HeadersSetter implements CarrierSetter<Map<String, String>> {
    public static final HeadersSetter INSTANCE = new HeadersSetter();

    @Override
    public void set(Map<String, String> carrier, String key, String value) {
      carrier.put(key, value);
    }
  }
}
