package datadog.trace.instrumentation.netty41.client;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.bootstrap.instrumentation.api.Tags.COMPONENT;
import static datadog.trace.bootstrap.instrumentation.api.Tags.SPAN_KIND;
import static datadog.trace.bootstrap.instrumentation.api.Tags.SPAN_KIND_INTERNAL;
import static datadog.trace.instrumentation.netty41.client.NettyHttpClientDecorator.NETTY_CLIENT;

import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import java.util.concurrent.TimeUnit;

public final class NettyClientResponseStream {
  public static final CharSequence NETTY_CLIENT_STREAM =
      UTF8BytesString.create("netty.client.stream");

  private static final CharSequence SSE = UTF8BytesString.create("sse");

  private final AgentSpan span;
  private final long startTimeNano;
  private long chunkCount;
  private boolean firstChunkSeen;
  private boolean finished;

  private NettyClientResponseStream(final AgentSpan parentSpan) {
    this.span = startSpan(NETTY_CLIENT.toString(), NETTY_CLIENT_STREAM);
    this.span.setResourceName("SSE stream " + parentSpan.getResourceName());
    this.span.setTag(COMPONENT, NETTY_CLIENT);
    this.span.setTag(SPAN_KIND, SPAN_KIND_INTERNAL);
    this.span.setTag("stream.type", SSE);
    this.startTimeNano = System.nanoTime();
  }

  public static NettyClientResponseStream startIfSse(
      final AgentSpan parentSpan, final HttpResponse response) {
    final String contentType = response.headers().get(HttpHeaderNames.CONTENT_TYPE);
    if (contentType == null || !isEventStream(contentType)) {
      return null;
    }
    return new NettyClientResponseStream(parentSpan);
  }

  public void onChunk() {
    if (finished) {
      return;
    }
    chunkCount++;
    if (!firstChunkSeen) {
      firstChunkSeen = true;
      span.setMetric(
          "stream.first_chunk.ms",
          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNano));
    }
  }

  public void finish() {
    if (!finished) {
      finished = true;
      span.setMetric("stream.chunk_count", chunkCount);
      span.finish();
    }
  }

  public void finishWithError(final Throwable throwable) {
    if (!finished) {
      span.addThrowable(throwable);
      finish();
    }
  }

  private static boolean isEventStream(final String contentType) {
    return contentType.regionMatches(true, 0, "text/event-stream", 0, "text/event-stream".length());
  }
}
