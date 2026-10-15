package datadog.trace.instrumentation.netty41.server;

import static datadog.trace.agent.test.assertions.SpanMatcher.span;
import static datadog.trace.agent.test.assertions.TraceMatcher.trace;
import static datadog.trace.api.config.TracerConfig.TRACE_RESPONSE_BODY_ENABLED;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.TRANSFER_ENCODING;
import static io.netty.handler.codec.http.HttpHeaderValues.CHUNKED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import datadog.trace.agent.test.assertions.SpanMatcher;
import datadog.trace.test.junit.utils.config.WithConfig;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpServerCodec;
import java.net.Socket;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@WithConfig(key = TRACE_RESPONSE_BODY_ENABLED, value = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NettyResponseBodyForkedTest extends NettyHttpServerTestSupport {

  private static final int MAX_CAPTURED_BODY_BYTES = 2 * 1024;
  private static final String FULL_RESPONSE_PATH = "/response/full";
  private static final String LAST_CONTENT_PATH = "/response/last-content";
  private static final String RAW_BYTES_PATH = "/response/raw-bytes";
  private static final String SSE_PATH = "/response/sse";
  private static final String BODY = "{\"message\":\"ok\"}";
  private static final String SSE_BODY = createSseBody();
  private static final String CAPTURED_SSE_BODY = SSE_BODY.substring(0, MAX_CAPTURED_BODY_BYTES);

  @Override
  protected void configurePipeline(Channel channel) {
    channel.pipeline().addLast(new HttpServerCodec());
    channel.pipeline().addLast(new ResponseHandler());
  }

  @Test
  void capturesFullHttpResponseBody() throws Exception {
    try (Socket socket = connect()) {
      socket.getOutputStream().write(request(FULL_RESPONSE_PATH).getBytes(US_ASCII));
      socket.getOutputStream().flush();

      assertEquals(BODY, readHttpResponseBody(socket.getInputStream()));
    }

    assertResponseBody(FULL_RESPONSE_PATH, BODY);
  }

  @Test
  void capturesBodyWrittenInLastHttpContent() throws Exception {
    try (Socket socket = connect()) {
      socket.getOutputStream().write(request(LAST_CONTENT_PATH).getBytes(US_ASCII));
      socket.getOutputStream().flush();

      assertEquals(BODY, readChunkedHttpResponseBody(socket.getInputStream()));
    }

    assertResponseBody(LAST_CONTENT_PATH, BODY);
  }

  @Test
  void capturesRawByteBufBody() throws Exception {
    try (Socket socket = connect()) {
      socket.getOutputStream().write(request(RAW_BYTES_PATH).getBytes(US_ASCII));
      socket.getOutputStream().flush();

      assertEquals(BODY, readHttpResponseBody(socket.getInputStream()));
    }

    assertResponseBody(RAW_BYTES_PATH, BODY);
  }

  @Test
  void capturesBoundedSseBodyWhenTheStreamCompletes() throws Exception {
    try (Socket socket = connect()) {
      socket.getOutputStream().write(request(SSE_PATH).getBytes(US_ASCII));
      socket.getOutputStream().flush();

      assertEquals(SSE_BODY, readChunkedHttpResponseBody(socket.getInputStream()));
    }

    assertResponseBody(SSE_PATH, CAPTURED_SSE_BODY);
  }

  private static String request(String path) {
    return "GET " + path + " HTTP/1.1\r\nHost: localhost\r\n\r\n";
  }

  private static String createSseBody() {
    StringBuilder body = new StringBuilder(MAX_CAPTURED_BODY_BYTES + 8);
    body.append("data: ");
    while (body.length() < MAX_CAPTURED_BODY_BYTES + 1) {
      body.append('a');
    }
    return body.append("\n\n").toString();
  }

  private void assertResponseBody(String path, String responseBody) throws Exception {
    writer.waitForTraces(1);
    assertEquals(responseBody, writer.firstTrace().get(0).getTag("response_body"));
    assertTraces(trace(serverSpan(path)));
  }

  private static SpanMatcher serverSpan(String path) {
    return span()
        .root()
        .operationName(Pattern.compile("netty\\.request"))
        .resourceName(Pattern.compile("GET " + Pattern.quote(path)))
        .type("web");
  }

  private static final class ResponseHandler extends SimpleChannelInboundHandler<HttpRequest> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest request) {
      if (LAST_CONTENT_PATH.equals(request.uri())) {
        writeChunkedResponse(ctx, "application/json", BODY);
        return;
      }
      if (SSE_PATH.equals(request.uri())) {
        writeChunkedResponse(ctx, "text/event-stream", SSE_BODY);
        return;
      }
      if (RAW_BYTES_PATH.equals(request.uri())) {
        DefaultHttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        response.headers().set(CONTENT_TYPE, "application/json");
        response.headers().set(CONTENT_LENGTH, BODY.getBytes(UTF_8).length);
        ctx.write(response);
        ctx.write(Unpooled.copiedBuffer(BODY, UTF_8));
        ctx.writeAndFlush(new DefaultLastHttpContent());
        return;
      }
      DefaultFullHttpResponse response =
          new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.copiedBuffer(BODY, UTF_8));
      response.headers().set(CONTENT_TYPE, "application/json");
      response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
      ctx.writeAndFlush(response);
    }

    private static void writeChunkedResponse(
        ChannelHandlerContext ctx, String contentType, String body) {
      DefaultHttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
      response.headers().set(CONTENT_TYPE, contentType);
      response.headers().set(TRANSFER_ENCODING, CHUNKED);
      ctx.write(response);
      ctx.writeAndFlush(
          new DefaultLastHttpContent(Unpooled.copiedBuffer(body, UTF_8)));
    }
  }
}
