package datadog.trace.instrumentation.netty38.server;

import static datadog.trace.instrumentation.netty38.server.NettyHttpServerDecorator.DECORATE;

import datadog.context.ContextScope;
import datadog.trace.api.Config;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.websocket.HandlerContext;
import datadog.trace.instrumentation.netty38.ChannelTraceContext;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

public class HttpServerResponseTracingHandler extends SimpleChannelDownstreamHandler {

  private final ContextStore<Channel, ChannelTraceContext> contextStore;
  private static final String UPGRADE_HEADER = "upgrade";

  public HttpServerResponseTracingHandler(
      final ContextStore<Channel, ChannelTraceContext> contextStore) {
    this.contextStore = contextStore;
  }

  @Override
  public void writeRequested(final ChannelHandlerContext ctx, final MessageEvent msg) {
    final ChannelTraceContext channelTraceContext =
        contextStore.putIfAbsent(ctx.getChannel(), ChannelTraceContext.Factory.INSTANCE);

    final AgentSpan span = channelTraceContext.getServerSpan();
    if (span == null) {
      ctx.sendDownstream(msg);
      return;
    }

    if (msg.getMessage() instanceof HttpResponse) {
      writeResponse(ctx, msg, channelTraceContext, span, (HttpResponse) msg.getMessage());
      return;
    }

    if (msg.getMessage() instanceof HttpChunk
        && channelTraceContext.getResponseBodyBuffer() != null) {
      writeResponseChunk(ctx, msg, channelTraceContext, span, (HttpChunk) msg.getMessage());
      return;
    }

    ctx.sendDownstream(msg);
  }

  private static void writeResponse(
      ChannelHandlerContext ctx,
      MessageEvent msg,
      ChannelTraceContext channelTraceContext,
      AgentSpan span,
      HttpResponse response) {
    try (final ContextScope scope = span.attachWithContext()) {
      addResponseHeaderTag(span, response.headers());
      final boolean finalResponse = isFinalResponse(response);
      final boolean captureResponseBody =
          finalResponse && shouldCaptureResponseBody(channelTraceContext, response.headers());
      if (captureResponseBody) {
        channelTraceContext.setResponseBody(
            new ByteArrayOutputStream(),
            HttpServerRequestTracingHandler.charsetName(
                response.headers().get(HttpHeaders.Names.CONTENT_TYPE),
                Config.get().getTracerResponseBodyEncoding()));
        if (!HttpHeaders.isTransferEncodingChunked(response)) {
          HttpServerRequestTracingHandler.append(
              channelTraceContext.getResponseBodyBuffer(), response.getContent());
        }
      }

      try {
        ctx.sendDownstream(msg);
      } catch (final Throwable throwable) {
        finishOnError(channelTraceContext, span, throwable);
        throw throwable;
      }
      final boolean isWebsocketUpgrade =
          response.getStatus() == HttpResponseStatus.SWITCHING_PROTOCOLS
              && "websocket".equals(response.headers().get(UPGRADE_HEADER));
      if (isWebsocketUpgrade) {
        String channelId = ctx.getChannel().getId().toString();
        channelTraceContext.setSenderHandlerContext(new HandlerContext.Sender(span, channelId));
      }
      if (finalResponse) {
        DECORATE.onResponse(span, response);
      }
      if (finalResponse
          && (!captureResponseBody
              || !HttpHeaders.isTransferEncodingChunked(response)
              || isEmptyResponse(response))) {
        if (captureResponseBody) {
          tagResponseBody(channelTraceContext, span);
        }
        DECORATE.beforeFinish(scope.context());
        span.finish(); // Finish the span manually since finishSpanOnClose was false
      }
    }
  }

  private static void writeResponseChunk(
      ChannelHandlerContext ctx,
      MessageEvent msg,
      ChannelTraceContext channelTraceContext,
      AgentSpan span,
      HttpChunk chunk) {
    try (final ContextScope scope = span.attachWithContext()) {
      HttpServerRequestTracingHandler.append(
          channelTraceContext.getResponseBodyBuffer(), chunk.getContent());
      try {
        ctx.sendDownstream(msg);
      } catch (final Throwable throwable) {
        finishOnError(channelTraceContext, span, throwable);
        throw throwable;
      }
      if (chunk.isLast()) {
        tagResponseBody(channelTraceContext, span);
        DECORATE.beforeFinish(scope.context());
        span.finish();
      }
    }
  }

  private static boolean shouldCaptureResponseBody(
      ChannelTraceContext channelTraceContext, HttpHeaders headers) {
    return Config.get().isTracerResponseBodyEnabled()
        && isResponseBodyAllowed(channelTraceContext.getRequestUri())
        && isSupportedResponseContentType(headers.get(HttpHeaders.Names.CONTENT_TYPE));
  }

  private static boolean isSupportedResponseContentType(String contentType) {
    if (contentType == null) {
      return false;
    }
    String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
    return normalizedContentType.contains("application/json")
        || normalizedContentType.contains("text/plain");
  }

  private static boolean isResponseBodyAllowed(String uri) {
    String blackList = Config.get().getTracerResponseBodyBlackListUrls();
    if (blackList != null
        && Arrays.stream(blackList.split(",")).anyMatch(candidate -> candidate.equals(uri))) {
      return false;
    }
    String whiteList = Config.get().getTracerResponseBodyWhiteListUrls();
    return whiteList == null || whiteList.isEmpty() || matchPath(uri, whiteList.split(","));
  }

  private static boolean matchPath(String requestPath, String[] patterns) {
    if (requestPath == null) {
      return false;
    }
    if (!requestPath.startsWith("/")) {
      requestPath = "/" + requestPath;
    }
    for (String pattern : patterns) {
      if (pattern.equals(requestPath)) {
        return true;
      }
      if (pattern.startsWith("*.")) {
        if (requestPath.endsWith(pattern.substring(1))) {
          return true;
        }
      } else if (pattern.contains("/*/")) {
        int wildcardIndex = pattern.indexOf("/*/");
        String prefix = pattern.substring(0, wildcardIndex);
        String suffix = pattern.substring(wildcardIndex + 2);
        if (requestPath.startsWith(prefix) && requestPath.endsWith(suffix)) {
          int prefixEndIndex = prefix.length();
          int suffixStartIndex = requestPath.length() - suffix.length();
          if (prefixEndIndex < suffixStartIndex) {
            String middlePart = requestPath.substring(prefixEndIndex + 1, suffixStartIndex);
            if (!middlePart.isEmpty() && middlePart.indexOf('/') == -1) {
              return true;
            }
          }
        }
      } else if (pattern.endsWith("*") && !pattern.endsWith("/*")) {
        String prefix = pattern.substring(0, pattern.length() - 1);
        if (requestPath.startsWith(prefix)) {
          return true;
        }
      } else if (pattern.endsWith("/*")) {
        String basePath = pattern.substring(0, pattern.length() - 2);
        if (requestPath.equals(basePath) || requestPath.startsWith(basePath + "/")) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isFinalResponse(HttpResponse response) {
    final boolean isWebsocketUpgrade =
        response.getStatus() == HttpResponseStatus.SWITCHING_PROTOCOLS
            && "websocket".equals(response.headers().get(UPGRADE_HEADER));
    return response.getStatus() != HttpResponseStatus.CONTINUE
        && (response.getStatus() != HttpResponseStatus.SWITCHING_PROTOCOLS || isWebsocketUpgrade);
  }

  private static boolean isEmptyResponse(HttpResponse response) {
    int statusCode = response.getStatus().getCode();
    if (statusCode == 204 || statusCode == 304) {
      return true;
    }
    String contentLength = response.headers().get(HttpHeaders.Names.CONTENT_LENGTH);
    if (contentLength == null) {
      return false;
    }
    try {
      return Long.parseLong(contentLength) == 0;
    } catch (NumberFormatException ignored) {
      return false;
    }
  }

  private static void tagResponseBody(ChannelTraceContext channelTraceContext, AgentSpan span) {
    ByteArrayOutputStream buffer = channelTraceContext.getResponseBodyBuffer();
    if (buffer == null) {
      return;
    }
    span.setTag(
        "response_body",
        new String(
            buffer.toByteArray(),
            HttpServerRequestTracingHandler.charset(
                channelTraceContext.getResponseBodyEncoding(), StandardCharsets.UTF_8)));
    channelTraceContext.clearResponseBody();
  }

  private static void finishOnError(
      ChannelTraceContext channelTraceContext, AgentSpan span, Throwable throwable) {
    channelTraceContext.clearResponseBody();
    DECORATE.onError(span, throwable);
    span.setHttpStatusCode(500);
    span.finish();
  }

  private static void addResponseHeaderTag(AgentSpan span, HttpHeaders headers) {
    if (Config.get().isTracerHeaderEnabled()) {
      span.setTag("response_header", HttpServerRequestTracingHandler.formatHeaders(headers, false));
    }
  }
}
