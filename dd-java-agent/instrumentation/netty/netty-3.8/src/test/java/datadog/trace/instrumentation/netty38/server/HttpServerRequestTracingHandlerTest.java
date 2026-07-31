package datadog.trace.instrumentation.netty38.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;

class HttpServerRequestTracingHandlerTest {

  @Test
  void capturesTheFirstTwoKiBWithoutChangingTheReaderIndex() {
    byte[] payload = new byte[2049];
    for (int i = 0; i < payload.length; i++) {
      payload[i] = 'a';
    }
    ChannelBuffer content = ChannelBuffers.wrappedBuffer(payload);
    content.readByte();
    int readerIndex = content.readerIndex();
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    HttpServerRequestTracingHandler.append(buffer, content);

    assertEquals(readerIndex, content.readerIndex());
    assertEquals(2048, buffer.size());
  }

  @Test
  void formatsHeadersLikeSpringMvc() {
    DefaultHttpRequest request =
        new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/payload");
    request.headers().add("X-Quoted", "a\"b");
    request.headers().add("X-Other", "value");

    assertEquals(
        "{\"X-Quoted\":\"ab\",\n\"X-Other\":\"value\"}",
        HttpServerRequestTracingHandler.formatHeaders(request.headers(), true));
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
}
