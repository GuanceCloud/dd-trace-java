package datadog.trace.instrumentation.netty40.server;

import static datadog.trace.instrumentation.netty40.AttributeKeys.RESPONSE_BODY_BUFFER_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty40.AttributeKeys.RESPONSE_BODY_ENCODING_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty40.AttributeKeys.RESPONSE_BODY_REMAINING_BYTES_ATTRIBUTE_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HttpServerRequestTracingHandlerTest {

  @Test
  void capturesTheFirstTwoKiBWithoutChangingTheReaderIndex() {
    byte[] payload = new byte[2049];
    for (int i = 0; i < payload.length; i++) {
      payload[i] = 'a';
    }
    ByteBuf content = Unpooled.wrappedBuffer(payload);
    content.readByte();
    int readerIndex = content.readerIndex();
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    HttpServerRequestTracingHandler.append(buffer, content);

    assertEquals(readerIndex, content.readerIndex());
    assertEquals(2048, buffer.size());
  }

  @Test
  void formatsHeadersLikeSpringMvc() {
    DefaultHttpHeaders headers = new DefaultHttpHeaders();
    headers.add("X-Quoted", "a\"b");
    headers.add("X-Other", "value");

    assertEquals(
        "{\"X-Quoted\":\"ab\",\n\"X-Other\":\"value\"}",
        HttpServerRequestTracingHandler.formatHeaders(headers, true));
  }

  @Test
  void usesTheDeclaredCharsetAndStripsTheQueryStringFromThePath() {
    assertEquals(
        "ISO-8859-1",
        HttpServerRequestTracingHandler.charsetName(
            "application/json; charset=ISO-8859-1", "UTF-8"));
    assertEquals("/payload", HttpServerRequestTracingHandler.requestPath("/payload?debug=true"));
    assertEquals(
        "é",
        new String(
            new byte[] {(byte) 0xE9},
            HttpServerRequestTracingHandler.charset("ISO-8859-1", StandardCharsets.UTF_8)));
  }

  @Test
  void tagsTheResponseBodyBufferedUntilTheChannelCloses() {
    EmbeddedChannel channel = new EmbeddedChannel();
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    buffer.write('{');
    buffer.write('}');
    channel.attr(RESPONSE_BODY_BUFFER_ATTRIBUTE_KEY).set(buffer);
    channel.attr(RESPONSE_BODY_ENCODING_ATTRIBUTE_KEY).set("UTF-8");
    String[] responseBody = new String[1];
    AgentSpan span =
        (AgentSpan)
            Proxy.newProxyInstance(
                AgentSpan.class.getClassLoader(),
                new Class<?>[] {AgentSpan.class},
                (proxy, method, arguments) -> {
                  if ("setTag".equals(method.getName()) && "response_body".equals(arguments[0])) {
                    responseBody[0] = (String) arguments[1];
                  }
                  return null;
                });

    HttpServerResponseTracingHandler.tagResponseBody(channel, span);

    assertEquals("{}", responseBody[0]);
    assertNull(channel.attr(RESPONSE_BODY_BUFFER_ATTRIBUTE_KEY).get());
  }

  @Test
  void completesTheResponseBodyAfterTheDeclaredContentLength() {
    EmbeddedChannel channel = new EmbeddedChannel();
    channel.attr(RESPONSE_BODY_REMAINING_BYTES_ATTRIBUTE_KEY).set(3L);

    assertFalse(HttpServerResponseTracingHandler.isResponseBodyComplete(channel, 2));
    assertTrue(HttpServerResponseTracingHandler.isResponseBodyComplete(channel, 1));
    assertNull(channel.attr(RESPONSE_BODY_REMAINING_BYTES_ATTRIBUTE_KEY).get());
  }
}
