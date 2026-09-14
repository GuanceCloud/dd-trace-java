package datadog.trace.instrumentation.netty41.client;

import static datadog.trace.instrumentation.netty41.AttributeKeys.CLIENT_REQUEST_START_NANOS_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty41.AttributeKeys.CLIENT_RESPONSE_STREAM_ATTRIBUTE_KEY;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datadog.trace.agent.test.AbstractInstrumentationTest;
import datadog.trace.core.DDSpan;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NettyClientResponseStreamTest extends AbstractInstrumentationTest {
  private static final String HEADERS =
      "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n"
          + "Transfer-Encoding: chunked\r\n\r\n";
  private static final String BODY = "d\r\ndata: first\n\n\r\n0\r\n\r\n";

  @Test
  void includesTheWaitBeforeHeadersWhenHeadersAndBodyArriveTogether() throws Exception {
    EmbeddedChannel channel = requestChannel();
    try {
      Thread.sleep(40);
      receive(channel, HEADERS + BODY);
      DDSpan stream = streamSpan();
      assertTrue(firstChunkMillis(stream) >= 40, "must include the wait before response headers");
      assertEquals(1L, ((Number) stream.getTag("stream.chunk_count")).longValue());
      assertNull(channel.attr(CLIENT_REQUEST_START_NANOS_ATTRIBUTE_KEY).get());
      assertNull(channel.attr(CLIENT_RESPONSE_STREAM_ATTRIBUTE_KEY).get());
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void includesBothTheWaitBeforeHeadersAndTheWaitBeforeBody() throws Exception {
    EmbeddedChannel channel = requestChannel();
    try {
      Thread.sleep(20);
      receive(channel, HEADERS);
      Thread.sleep(20);
      receive(channel, BODY);
      assertTrue(firstChunkMillis(streamSpan()) >= 40);
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void doesNotTreatAnEmptyContentLengthResponseAsTheFirstChunk() throws Exception {
    assertEmptyResponse(
        "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: 0\r\n\r\n");
  }

  @Test
  void doesNotTreatAnEmptyChunkedResponseAsTheFirstChunk() throws Exception {
    assertEmptyResponse(HEADERS + "0\r\n\r\n");
  }

  @Test
  void doesNotTreatAnEmptyFullResponseAsTheFirstChunk() throws Exception {
    EmbeddedChannel channel = requestChannel();
    try {
      DefaultFullHttpResponse response =
          new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
      response.headers().set("Content-Type", "text/event-stream");
      channel.writeInbound(response);
      drainInbound(channel);
      DDSpan stream = streamSpan();
      assertNull(stream.getTag("stream.first_chunk.ms"));
      assertEquals(0L, ((Number) stream.getTag("stream.chunk_count")).longValue());
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void ignoresEmptyIntermediateContentBeforeTheFirstReadableChunk() throws Exception {
    EmbeddedChannel channel = requestChannel();
    try {
      receive(channel, HEADERS);
      channel.writeInbound(new DefaultHttpContent(Unpooled.EMPTY_BUFFER));
      drainInbound(channel);
      Thread.sleep(20);
      receive(channel, BODY);
      DDSpan stream = streamSpan();
      assertTrue(firstChunkMillis(stream) >= 20);
      assertEquals(1L, ((Number) stream.getTag("stream.chunk_count")).longValue());
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void doesNotInventLatencyWhenTheRequestStartIsUnavailable() throws Exception {
    EmbeddedChannel channel = requestChannel();
    try {
      channel.attr(CLIENT_REQUEST_START_NANOS_ATTRIBUTE_KEY).set(null);
      receive(channel, HEADERS + BODY);
      DDSpan stream = streamSpan();
      assertNull(stream.getTag("stream.first_chunk.ms"));
      assertEquals(1L, ((Number) stream.getTag("stream.chunk_count")).longValue());
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void leavesTheFirstChunkAbsentWhenDisconnectedBeforeBody() throws Exception {
    EmbeddedChannel channel = requestChannel();
    try {
      receive(channel, HEADERS);
      channel.close().sync();
      DDSpan stream = streamSpan();
      assertNull(stream.getTag("stream.first_chunk.ms"));
      assertEquals(0L, ((Number) stream.getTag("stream.chunk_count")).longValue());
      assertNull(channel.attr(CLIENT_REQUEST_START_NANOS_ATTRIBUTE_KEY).get());
      assertNull(channel.attr(CLIENT_RESPONSE_STREAM_ATTRIBUTE_KEY).get());
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void retainsTheRequestStartAcrossInformationalResponses() throws Exception {
    EmbeddedChannel channel = requestChannel();
    try {
      Long requestStart = channel.attr(CLIENT_REQUEST_START_NANOS_ATTRIBUTE_KEY).get();
      receive(channel, "HTTP/1.1 100 Continue\r\n\r\n");
      assertEquals(requestStart, channel.attr(CLIENT_REQUEST_START_NANOS_ATTRIBUTE_KEY).get());
      Thread.sleep(20);
      receive(channel, HEADERS + BODY);
      assertTrue(firstChunkMillis(streamSpan()) >= 20);
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void clearsTheRequestStartForNonStreamingResponsesAndReplacesItOnReuse() throws Exception {
    EmbeddedChannel channel = requestChannel();
    try {
      Long firstStart = channel.attr(CLIENT_REQUEST_START_NANOS_ATTRIBUTE_KEY).get();
      receive(channel, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
      assertNull(channel.attr(CLIENT_REQUEST_START_NANOS_ATTRIBUTE_KEY).get());
      writeRequest(channel);
      Long secondStart = channel.attr(CLIENT_REQUEST_START_NANOS_ATTRIBUTE_KEY).get();
      assertNotNull(secondStart);
      assertTrue(secondStart > firstStart);
      Thread.sleep(20);
      receive(channel, HEADERS + BODY);
      writer.waitForTraces(2);
      assertTrue(firstChunkMillis(streamSpan()) >= 20);
      assertNull(channel.attr(CLIENT_REQUEST_START_NANOS_ATTRIBUTE_KEY).get());
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void clearsTheRequestStartWhenTheConnectionClosesBeforeHeaders() throws Exception {
    EmbeddedChannel channel = requestChannel();
    channel.close().sync();
    assertNull(channel.attr(CLIENT_REQUEST_START_NANOS_ATTRIBUTE_KEY).get());
    channel.finishAndReleaseAll();
    writer.waitForTraces(1);
    assertEquals(1, writer.get(0).size());
  }

  @Test
  void clearsTheRequestStartWhenTheConnectionFailsBeforeHeaders() throws Exception {
    EmbeddedChannel channel = requestChannel();
    try {
      // Consume the propagated exception so EmbeddedChannel does not rethrow it during cleanup.
      channel
          .pipeline()
          .addLast(
              new ChannelInboundHandlerAdapter() {
                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {}
              });
      channel.pipeline().fireExceptionCaught(new IOException("connection failed"));
      assertNull(channel.attr(CLIENT_REQUEST_START_NANOS_ATTRIBUTE_KEY).get());
      writer.waitForTraces(1);
      assertEquals(1, writer.get(0).size());
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  private void assertEmptyResponse(String response) throws Exception {
    EmbeddedChannel channel = requestChannel();
    try {
      receive(channel, response);
      DDSpan stream = streamSpan();
      assertNull(stream.getTag("stream.first_chunk.ms"));
      assertEquals(0L, ((Number) stream.getTag("stream.chunk_count")).longValue());
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  private static EmbeddedChannel requestChannel() {
    // The instrumentation inserts the production request/response handlers after this codec.
    EmbeddedChannel channel = new EmbeddedChannel(new HttpClientCodec());
    writeRequest(channel);
    assertNotNull(channel.attr(CLIENT_REQUEST_START_NANOS_ATTRIBUTE_KEY).get());
    return channel;
  }

  private static void writeRequest(EmbeddedChannel channel) {
    channel.writeOutbound(
        new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/stream"));
    Object message;
    while ((message = channel.readOutbound()) != null) {
      ReferenceCountUtil.release(message);
    }
  }

  private static void receive(EmbeddedChannel channel, String response) {
    channel.writeInbound(Unpooled.copiedBuffer(response, US_ASCII));
    drainInbound(channel);
  }

  private static void drainInbound(EmbeddedChannel channel) {
    Object message;
    while ((message = channel.readInbound()) != null) {
      ReferenceCountUtil.release(message);
    }
  }

  private DDSpan streamSpan() throws Exception {
    writer.waitForTraces(1);
    return writer.stream()
        .flatMap(trace -> trace.stream())
        .filter(span -> "netty.client.stream".contentEquals(span.getOperationName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("SSE stream span was not reported"));
  }

  private static double firstChunkMillis(DDSpan stream) {
    Object value = stream.getTag("stream.first_chunk.ms");
    assertNotNull(value);
    assertTrue(value instanceof Double, "first chunk latency must retain fractional milliseconds");
    return ((Number) value).doubleValue();
  }
}
