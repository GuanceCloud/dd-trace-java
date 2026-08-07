package datadog.trace.instrumentation.netty38.server;

import static datadog.trace.instrumentation.netty38.server.NettyHttpServerDecorator.DECORATE;

import datadog.context.Context;
import datadog.context.ContextScope;
import datadog.trace.api.Config;
import datadog.trace.api.gateway.Flow;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.netty38.ChannelTraceContext;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;

public class HttpServerRequestTracingHandler extends SimpleChannelUpstreamHandler {

  private static final int MAX_CAPTURED_BODY_BYTES = 2 * 1024;
  private final ContextStore<Channel, ChannelTraceContext> contextStore;

  public HttpServerRequestTracingHandler(
      final ContextStore<Channel, ChannelTraceContext> contextStore) {
    this.contextStore = contextStore;
  }

  @Override
  public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent msg) {
    final ChannelTraceContext channelTraceContext =
        contextStore.putIfAbsent(ctx.getChannel(), ChannelTraceContext.Factory.INSTANCE);

    if (!(msg.getMessage() instanceof HttpRequest)) {
      final Context storedContext = channelTraceContext.getServerContext();
      captureRequestBody(channelTraceContext, AgentSpan.fromContext(storedContext), msg.getMessage());
      if (storedContext == null) {
        ctx.sendUpstream(msg); // superclass does not throw
      } else {
        try (final ContextScope scope = storedContext.attach()) {
          ctx.sendUpstream(msg); // superclass does not throw
        }
      }
      return;
    }

    final HttpRequest request = (HttpRequest) msg.getMessage();
    final HttpHeaders headers = request.headers();
    final Context parentContext = DECORATE.extract(headers);
    final Context context = DECORATE.startSpan(headers, parentContext);

    channelTraceContext.reset();
    channelTraceContext.setRequestHeaders(headers);
    channelTraceContext.setRequestUri(requestPath(request.getUri()));

    try (final ContextScope scope = context.attach()) {
      final AgentSpan span = AgentSpan.fromContext(context);
      DECORATE.afterStart(span);
      DECORATE.onRequest(span, ctx.getChannel(), request, parentContext);
      addRequestHeaderTag(span, headers);

      channelTraceContext.setServerContext(context);
      if (shouldCaptureRequestBody(request)) {
        channelTraceContext.setRequestBody(
            new ByteArrayOutputStream(),
            charsetName(
                headers.get(HttpHeaders.Names.CONTENT_TYPE), StandardCharsets.UTF_8.name()));
        if (!HttpHeaders.isTransferEncodingChunked(request)) {
          captureRequestBody(channelTraceContext, span, request.getContent());
          tagRequestBody(channelTraceContext, span);
        }
      }

      Flow.Action.RequestBlockingAction rba = span.getRequestBlockingAction();
      if (rba != null) {
        ctx.getPipeline()
            .addAfter(
                ctx.getName(),
                "blocking_handler",
                new BlockingResponseHandler(span.getRequestContext().getTraceSegment(), rba));
      }

      try {
        ctx.sendUpstream(msg);
      } catch (final Throwable throwable) {
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(scope.context());
        span.finish(); // Finish the span manually since finishSpanOnClose was false
        throw throwable;
      }
    }
  }

  private static void captureRequestBody(
      ChannelTraceContext channelTraceContext, AgentSpan span, Object message) {
    if (span == null || !(message instanceof HttpChunk)) {
      return;
    }
    HttpChunk chunk = (HttpChunk) message;
    captureRequestBody(channelTraceContext, span, chunk.getContent());
    if (chunk.isLast()) {
      tagRequestBody(channelTraceContext, span);
    }
  }

  private static void captureRequestBody(
      ChannelTraceContext channelTraceContext, AgentSpan span, ChannelBuffer content) {
    if (span == null || channelTraceContext.getRequestBodyBuffer() == null) {
      return;
    }
    append(channelTraceContext.getRequestBodyBuffer(), content);
  }

  private static void tagRequestBody(ChannelTraceContext channelTraceContext, AgentSpan span) {
    ByteArrayOutputStream buffer = channelTraceContext.getRequestBodyBuffer();
    if (buffer == null) {
      return;
    }
    span.setTag(
        "request_body",
        new String(
            buffer.toByteArray(),
            charset(channelTraceContext.getRequestBodyEncoding(), StandardCharsets.UTF_8)));
    channelTraceContext.clearRequestBody();
  }

  private static boolean shouldCaptureRequestBody(HttpRequest request) {
    return Config.get().isTracerRequestBodyEnabled()
        && "POST".equalsIgnoreCase(request.getMethod().getName())
        && isSupportedRequestContentType(request.headers().get(HttpHeaders.Names.CONTENT_TYPE));
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

  static void append(ByteArrayOutputStream buffer, ChannelBuffer content) {
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
