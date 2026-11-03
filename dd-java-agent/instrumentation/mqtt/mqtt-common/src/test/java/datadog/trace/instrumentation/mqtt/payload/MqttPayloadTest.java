package datadog.trace.instrumentation.mqtt.payload;

import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MqttPayloadTest {
  private static final Map<String, String> HEADERS =
      singletonMap("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");

  @Test
  void preservesBinaryPayloadAndBufferPosition() {
    byte[] original = new byte[259];
    for (int i = 0; i < original.length; i++) {
      original[i] = (byte) i;
    }
    ByteBuffer input = ByteBuffer.wrap(original);
    input.position(3);
    byte[] framed = MqttPayload.encode(HEADERS, input);
    assertEquals(3, input.position());
    MqttPayload.Decoded decoded = MqttPayload.decode(ByteBuffer.wrap(framed));
    assertNotNull(decoded);
    assertArrayEquals(
        Arrays.copyOfRange(original, 3, original.length),
        Arrays.copyOfRange(framed, decoded.payloadOffset, framed.length));
  }

  @Test
  void replacesContextWithoutNestingEnvelopes() {
    byte[] body = {0, -1, 42, 0};
    byte[] first = MqttPayload.encode(HEADERS, ByteBuffer.wrap(body));
    byte[] second = MqttPayload.reframe(HEADERS, ByteBuffer.wrap(first));
    assertArrayEquals(first, second);
    MqttPayload.Decoded decoded = MqttPayload.decode(ByteBuffer.wrap(second));
    assertArrayEquals(body, Arrays.copyOfRange(second, decoded.payloadOffset, second.length));
  }

  @Test
  void preservesAValidFrameWhenItIsLiteralBusinessData() {
    byte[] businessFrame = MqttPayload.encode(HEADERS, ByteBuffer.wrap(new byte[] {1, 2}));
    byte[] wire = MqttPayload.encode(HEADERS, ByteBuffer.wrap(businessFrame));
    MqttPayload.Decoded decoded = MqttPayload.decode(ByteBuffer.wrap(wire));
    assertArrayEquals(businessFrame, Arrays.copyOfRange(wire, decoded.payloadOffset, wire.length));
  }

  @Test
  void supportsEmptyBody() {
    byte[] framed = MqttPayload.encode(HEADERS, ByteBuffer.wrap(new byte[0]));
    assertEquals(0, MqttPayload.decode(ByteBuffer.wrap(framed)).payloadLength);
  }

  @Test
  void rejectsOrdinaryAndTruncatedPayloads() {
    assertNull(MqttPayload.decode(ByteBuffer.wrap(new byte[] {1, 2, 3})));
    byte[] valid = MqttPayload.encode(HEADERS, ByteBuffer.wrap(new byte[] {1}));
    for (int size = 0; size < valid.length - 1; size++) {
      assertNull(MqttPayload.decode(ByteBuffer.wrap(Arrays.copyOf(valid, size))));
    }
  }

  @Test
  void rejectsInvalidVersionAndUnboundedHeaderLengths() {
    byte[] valid = MqttPayload.encode(HEADERS, ByteBuffer.wrap(new byte[] {1}));
    byte[] version = valid.clone();
    version[12] = 2;
    assertNull(MqttPayload.decode(ByteBuffer.wrap(version)));
    for (int length : new int[] {-1, 0, 8193, Integer.MAX_VALUE}) {
      byte[] broken = valid.clone();
      ByteBuffer.wrap(broken).putInt(16, length);
      assertNull(MqttPayload.decode(ByteBuffer.wrap(broken)));
    }
    byte[] count = valid.clone();
    ByteBuffer.wrap(count).putShort(20, (short) 65);
    assertNull(MqttPayload.decode(ByteBuffer.wrap(count)));
  }

  @Test
  void refusesExcessiveMetadataOnEncode() {
    assertNull(MqttPayload.encode(new LinkedHashMap<String, String>(), ByteBuffer.allocate(0)));
    char[] value = new char[8192];
    Arrays.fill(value, 'a');
    assertNull(
        MqttPayload.encode(singletonMap("baggage", new String(value)), ByteBuffer.allocate(0)));
    Map<String, String> tooMany = new LinkedHashMap<>();
    for (int i = 0; i < 65; i++) {
      tooMany.put("key" + i, "value");
    }
    assertNull(MqttPayload.encode(tooMany, ByteBuffer.allocate(0)));
  }
}
