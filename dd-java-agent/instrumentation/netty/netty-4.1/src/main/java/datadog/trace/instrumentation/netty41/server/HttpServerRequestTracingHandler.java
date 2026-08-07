package datadog.trace.instrumentation.netty41.server;

import static datadog.trace.instrumentation.netty41.AttributeKeys.CONTEXT_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty41.AttributeKeys.PARENT_CONTEXT_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty41.AttributeKeys.REQUEST_BODY_BUFFER_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty41.AttributeKeys.REQUEST_BODY_ENCODING_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty41.AttributeKeys.REQUEST_HEADERS_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty41.AttributeKeys.REQUEST_METHOD_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty41.AttributeKeys.REQUEST_URI_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty41.AttributeKeys.RESPONSE_BODY_BUFFER_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty41.AttributeKeys.RESPONSE_BODY_ENCODING_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty41.AttributeKeys.RESPONSE_BODY_REMAINING_BYTES_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty41.server.NettyHttpServerDecorator.DECORATE;

import datadog.context.Context;
import datadog.context.ContextScope;
import datadog.trace.api.Config;
import datadog.trace.api.gateway.Flow;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.netty41.ServerRequestContext;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;

@ChannelHandler.Sharable
public class HttpServerRequestTracingHandler extends ChannelInboundHandlerAdapter {
  public static HttpServerRequestTracingHandler INSTANCE = new HttpServerRequestTracingHandler();
  private static final int MAX_CAPTURED_BODY_BYTES = 2 * 1024;

  @Override
  public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
    Channel channel = ctx.channel();
    if (!(msg instanceof HttpRequest)) {
      final Context storedContext = channel.attr(CONTEXT_ATTRIBUTE_KEY).get();
      captureRequestBody(channel, AgentSpan.fromContext(storedContext), msg);
      if (storedContext == null) {
        ctx.fireChannelRead(msg); // superclass does not throw
      } else {
        try (final ContextScope scope = storedContext.attach()) {
          ctx.fireChannelRead(msg); // superclass does not throw
        }
      }
      return;
    }

    final HttpRequest request = (HttpRequest) msg;
    if (ServerRequestContext.isRequestBlocked(channel)) {
      // A deferred block keeps its handler in the pipeline while an earlier response completes.
      // Forward later pipelined requests to that handler instead of adding another one.
      ctx.fireChannelRead(msg);
      return;
    }
    if (!ServerRequestContext.canTrackRequest(channel)) {
      channel.attr(PARENT_CONTEXT_ATTRIBUTE_KEY).remove();
      ctx.fireChannelRead(msg);
      return;
    }

    final HttpHeaders headers = request.headers();
    final Context storedParentContext = channel.attr(PARENT_CONTEXT_ATTRIBUTE_KEY).getAndRemove();
    final Context parentContext =
        storedParentContext != null ? storedParentContext : DECORATE.extract(headers);
    final Context context = DECORATE.startSpan(headers, parentContext);
    final ServerRequestContext serverContext =
        ServerRequestContext.add(channel, context, headers.get("accept"));

    try (final ContextScope ignored = context.attach()) {
      final AgentSpan span = AgentSpan.fromContext(context);
      DECORATE.afterStart(span);
      DECORATE.onRequest(span, channel, request, parentContext);

      addRequestHeaderTag(span, headers);
      channel.attr(CONTEXT_ATTRIBUTE_KEY).set(context);
      channel.attr(REQUEST_HEADERS_ATTRIBUTE_KEY).set(request.headers());
      channel.attr(REQUEST_METHOD_ATTRIBUTE_KEY).set(request.method().name());
      channel.attr(REQUEST_URI_ATTRIBUTE_KEY).set(requestPath(request.uri()));
      channel.attr(REQUEST_BODY_BUFFER_ATTRIBUTE_KEY).remove();
      channel.attr(REQUEST_BODY_ENCODING_ATTRIBUTE_KEY).remove();
      channel.attr(RESPONSE_BODY_BUFFER_ATTRIBUTE_KEY).remove();
      channel.attr(RESPONSE_BODY_ENCODING_ATTRIBUTE_KEY).remove();
      channel.attr(RESPONSE_BODY_REMAINING_BYTES_ATTRIBUTE_KEY).remove();

      if (shouldCaptureRequestBody(request)) {
        channel.attr(REQUEST_BODY_BUFFER_ATTRIBUTE_KEY).set(new ByteArrayOutputStream());
        channel
            .attr(REQUEST_BODY_ENCODING_ATTRIBUTE_KEY)
            .set(
                charsetName(
                    headers.get(HttpHeaderNames.CONTENT_TYPE), StandardCharsets.UTF_8.name()));
      }

      Flow.Action.RequestBlockingAction rba = span.getRequestBlockingAction();
      if (rba != null) {
        ctx.pipeline()
            .addAfter(
                ctx.name(),
                BlockingResponseHandler.HANDLER_NAME,
                new BlockingResponseHandler(
                    span.getRequestContext().getTraceSegment(), rba, serverContext));
      }

      try {
        ctx.fireChannelRead(msg);
        /*
        The handler chain started from 'fireChannelRead(msg)' will finish the span if successful
        */
      } catch (final Throwable throwable) {
        /*
        The handler chain failed with exception - need to finish the span here
         */
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(ignored.context());
        span.finish(); // Finish the span manually since finishSpanOnClose was false
        ServerRequestContext.remove(ctx.channel(), serverContext);
        throw throwable;
      }
    }
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    try {
      super.channelInactive(ctx);
    } finally {
      try {
        final Deque<ServerRequestContext> storedContexts =
            ServerRequestContext.removeAll(ctx.channel());
        if (storedContexts != null) {
          ServerRequestContext storedContext;
          while ((storedContext = storedContexts.pollFirst()) != null) {
            if (storedContext.isResponseStarted()) {
              finishSpanOnChannelClose(storedContext);
            } else {
              publishSpanOnChannelClose(storedContext.tracingContext());
            }
          }
        }
      } catch (final Throwable ignored) {
      }
    }
  }

  private static void finishSpanOnChannelClose(final ServerRequestContext serverContext) {
    final Context storedContext = serverContext.tracingContext();
    final AgentSpan span = AgentSpan.fromContext(storedContext);
    if (span == null) {
      return;
    }
    try (final ContextScope ignored = storedContext.attach()) {
      if (!serverContext.isBeforeFinishCalled()) {
        serverContext.markBeforeFinishCalled();
        DECORATE.beforeFinish(storedContext);
      }
      span.finish();
    }
  }
  private static void publishSpanOnChannelClose(final Context storedContext) {
    final AgentSpan span = AgentSpan.fromContext(storedContext);
    if (span != null && span.phasedFinish()) {
      // At this point we can just publish this span to avoid losing the rest of the trace.
      span.publish();
    }
  }

  static void captureRequestBody(Channel channel, AgentSpan span, Object msg) {
    if (span == null || !(msg instanceof HttpContent)) {
      return;
    }
    final ByteArrayOutputStream buffer = channel.attr(REQUEST_BODY_BUFFER_ATTRIBUTE_KEY).get();
    if (buffer == null) {
      return;
    }
    append(buffer, ((HttpContent) msg).content());
    if (msg instanceof LastHttpContent) {
      span.setTag(
          "request_body",
          new String(
              buffer.toByteArray(),
              charset(
                  channel.attr(REQUEST_BODY_ENCODING_ATTRIBUTE_KEY).get(),
                  StandardCharsets.UTF_8)));
      channel.attr(REQUEST_BODY_BUFFER_ATTRIBUTE_KEY).remove();
      channel.attr(REQUEST_BODY_ENCODING_ATTRIBUTE_KEY).remove();
    }
  }

  private static boolean shouldCaptureRequestBody(HttpRequest request) {
    return Config.get().isTracerRequestBodyEnabled()
        && "POST".equalsIgnoreCase(request.method().name())
        && isSupportedRequestContentType(request.headers().get(HttpHeaderNames.CONTENT_TYPE));
  }

  private static boolean isSupportedRequestContentType(String contentType) {
    if (contentType == null) {
      return false;
    }
    String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
    return normalizedContentType.contains("application/json")
        || normalizedContentType.contains("text/x-gwt-rpc");
  }

  private static void addRequestHeaderTag(AgentSpan span, HttpHeaders headers) {
    if (Config.get().isTracerHeaderEnabled()) {
      span.setTag("request_header", formatHeaders(headers, true));
    }
  }

  static String formatHeaders(HttpHeaders headers, boolean removeQuotes) {
    StringBuilder formattedHeaders = new StringBuilder();
    int count = 0;
    for (Map.Entry<String, String> entry : headers.entries()) {
      if (count == 0) {
        formattedHeaders.append("{");
      } else {
        formattedHeaders.append(",\n");
      }
      String value = entry.getValue();
      formattedHeaders
          .append("\"")
          .append(entry.getKey())
          .append("\":\"")
          .append(removeQuotes ? value.replace("\"", "") : value)
          .append("\"");
      count++;
    }
    if (count > 0) {
      formattedHeaders.append("}");
    }
    return formattedHeaders.toString();
  }

  static void append(ByteArrayOutputStream buffer, ByteBuf content) {
    int length = Math.min(content.readableBytes(), MAX_CAPTURED_BODY_BYTES - buffer.size());
    if (length <= 0) {
      return;
    }
    byte[] bytes = new byte[length];
    content.getBytes(content.readerIndex(), bytes);
    buffer.write(bytes, 0, bytes.length);
  }

  static Charset charset(String name, Charset defaultCharset) {
    if (name == null || name.isEmpty()) {
      return defaultCharset;
    }
    try {
      return Charset.forName(name);
    } catch (IllegalArgumentException ignored) {
      return defaultCharset;
    }
  }

  static String charsetName(String contentType, String defaultName) {
    if (contentType == null) {
      return defaultName;
    }
    String[] parts = contentType.split(";");
    for (int i = 1; i < parts.length; i++) {
      String part = parts[i].trim();
      if (part.regionMatches(true, 0, "charset=", 0, "charset=".length())) {
        return part.substring("charset=".length()).replace("\"", "").trim();
      }
    }
    return defaultName;
  }

  static String requestPath(String uri) {
    int queryStart = uri.indexOf('?');
    return queryStart < 0 ? uri : uri.substring(0, queryStart);
  }
}
