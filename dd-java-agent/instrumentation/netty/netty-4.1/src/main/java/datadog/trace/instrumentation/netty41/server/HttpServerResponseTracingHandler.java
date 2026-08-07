package datadog.trace.instrumentation.netty41.server;

import static datadog.trace.instrumentation.netty41.AttributeKeys.CONTEXT_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty41.AttributeKeys.REQUEST_METHOD_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty41.AttributeKeys.REQUEST_URI_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty41.AttributeKeys.RESPONSE_BODY_BUFFER_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty41.AttributeKeys.RESPONSE_BODY_ENCODING_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty41.AttributeKeys.RESPONSE_BODY_REMAINING_BYTES_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty41.AttributeKeys.WEBSOCKET_SENDER_HANDLER_CONTEXT;
import static datadog.trace.instrumentation.netty41.server.NettyHttpServerDecorator.DECORATE;

import datadog.context.Context;
import datadog.context.ContextScope;
import datadog.trace.api.Config;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.websocket.HandlerContext;
import datadog.trace.instrumentation.netty41.ServerRequestContext;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.concurrent.Future;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

@ChannelHandler.Sharable
public class HttpServerResponseTracingHandler extends ChannelOutboundHandlerAdapter {
  public static HttpServerResponseTracingHandler INSTANCE = new HttpServerResponseTracingHandler();

  @Override
  public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise prm) {
    final boolean isResponse = msg instanceof HttpResponse;
    final boolean isLastContent = msg instanceof LastHttpContent;
    if (!isResponse && !isLastContent) {
      final Context storedContext = ctx.channel().attr(CONTEXT_ATTRIBUTE_KEY).get();
      final AgentSpan span = AgentSpan.fromContext(storedContext);
      if (span != null && ctx.channel().attr(RESPONSE_BODY_BUFFER_ATTRIBUTE_KEY).get() != null) {
        if (msg instanceof HttpContent) {
          writeResponseContent(ctx, msg, prm, storedContext, span, (HttpContent) msg);
          return;
        }
        if (msg instanceof ByteBuf) {
          writeResponseBytes(ctx, msg, prm, storedContext, span, (ByteBuf) msg);
          return;
        }
      }
      ctx.write(msg, prm);
      return;
    }

    final ServerRequestContext serverContext = ServerRequestContext.nextResponse(ctx.channel());
    if (!isResponse && serverContext != null && !serverContext.isResponseStarted()) {
      ctx.write(msg, prm);
      return;
    }

    final Context storedContext =
        serverContext == null
            // HTTP/2 multiplex stream channels only inherit the mirrored context attribute from
            // Http2MultiplexHandlerStreamChannelInstrumentation.PropagateContextAdvice, without a
            // per-stream request queue.
            ? ctx.channel().attr(CONTEXT_ATTRIBUTE_KEY).get()
            : serverContext.tracingContext();
    final AgentSpan span = AgentSpan.fromContext(storedContext);

    if (span == null) {
      ctx.write(msg, prm);
      return;
    }

    try (final ContextScope ignored = storedContext.attach()) {
      final HttpResponse response = isResponse ? (HttpResponse) msg : null;
      final boolean websocketUpgrade = response != null && isWebsocketUpgrade(response);
      final boolean informationalResponse =
          response != null && isInformationalResponse(response) && !websocketUpgrade;
      final boolean finishResponseOnWrite = isLastContent && !informationalResponse;
      final ChannelPromise writePromise =
          finishResponseOnWrite && prm.isVoid() ? ctx.newPromise() : prm;
      try {
        if (response != null && !informationalResponse) {
          onResponse(ctx, span, serverContext, response, websocketUpgrade);
        }
        if (finishResponseOnWrite) {
          removeServerContext(ctx, serverContext);
          writePromise.addListener(
              future -> finishSpan(serverContext, storedContext, span, future, ctx.channel()));
        }
        ctx.write(msg, writePromise);
        if (finishResponseOnWrite && (!writePromise.isDone() || writePromise.isSuccess())) {
          final ServerRequestContext nextResponse =
              ServerRequestContext.nextResponse(ctx.channel());
          BlockingResponseHandler.maybeWriteDeferredBlockResponse(ctx, nextResponse);
        }
      } catch (final Throwable throwable) {
        if (!finishResponseOnWrite || !writePromise.isDone()) {
          DECORATE.onError(span, throwable);
          span.setHttpStatusCode(500);
          if (!finishResponseOnWrite) {
            removeServerContext(ctx, serverContext);
          }
          finishSpan(serverContext, storedContext, span, ctx.channel());
        }
        throw throwable;
      }
    }
  }

  private static void writeResponseContent(
      ChannelHandlerContext ctx,
      Object msg,
      ChannelPromise prm,
      Context storedContext,
      AgentSpan span,
      HttpContent content) {
    try (final ContextScope scope = storedContext.attach()) {
      HttpServerRequestTracingHandler.append(
          ctx.channel().attr(RESPONSE_BODY_BUFFER_ATTRIBUTE_KEY).get(), content.content());
      isResponseBodyComplete(ctx.channel(), content.content().readableBytes());
      try {
        ctx.write(msg, prm);
      } catch (final Throwable throwable) {
        DECORATE.onError(span, throwable);
        span.setHttpStatusCode(500);
        throw throwable;
      }
    }
  }

  private static void writeResponseBytes(
      ChannelHandlerContext ctx,
      Object msg,
      ChannelPromise prm,
      Context storedContext,
      AgentSpan span,
      ByteBuf content) {
    try (final ContextScope scope = storedContext.attach()) {
      HttpServerRequestTracingHandler.append(
          ctx.channel().attr(RESPONSE_BODY_BUFFER_ATTRIBUTE_KEY).get(), content);
      isResponseBodyComplete(ctx.channel(), content.readableBytes());
      try {
        ctx.write(msg, prm);
      } catch (final Throwable throwable) {
        DECORATE.onError(span, throwable);
        span.setHttpStatusCode(500);
        throw throwable;
      }
    }
  }

  private static void onResponse(
      final ChannelHandlerContext ctx,
      final AgentSpan span,
      final ServerRequestContext serverContext,
      final HttpResponse response,
      final boolean websocketUpgrade) {
    if (websocketUpgrade) {
      ctx.channel()
          .attr(WEBSOCKET_SENDER_HANDLER_CONTEXT)
          .set(new HandlerContext.Sender(span, ctx.channel().id().asShortText()));
    }
    span.setTag("ext_trace_id", span.getTraceId().toString());
    addResponseHeaderTag(span, response.headers());
    if (shouldCaptureResponseBody(ctx, response.headers())) {
      ctx.channel().attr(RESPONSE_BODY_BUFFER_ATTRIBUTE_KEY).set(new ByteArrayOutputStream());
      ctx.channel()
          .attr(RESPONSE_BODY_ENCODING_ATTRIBUTE_KEY)
          .set(
              HttpServerRequestTracingHandler.charsetName(
                  response.headers().get(HttpHeaderNames.CONTENT_TYPE),
                  Config.get().getTracerResponseBodyEncoding()));
      initializeResponseBodyLength(ctx.channel(), response.headers(), 0);
    }
    DECORATE.onResponse(span, response);
    if (serverContext != null) {
      serverContext.markResponseStarted();
    }
  }

  private static boolean shouldCaptureResponseBody(ChannelHandlerContext ctx, HttpHeaders headers) {
    return Config.get().isTracerResponseBodyEnabled()
        && isResponseBodyAllowed(ctx.channel().attr(REQUEST_URI_ATTRIBUTE_KEY).get())
        && isSupportedResponseContentType(headers.get(HttpHeaderNames.CONTENT_TYPE));
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

  private static boolean isInformationalResponse(final HttpResponse response) {
    final int statusCode = response.status().code();
    return statusCode >= 100 && statusCode < 200;
  }

  private static boolean isWebsocketUpgrade(final HttpResponse response) {
    return response.status().code() == HttpResponseStatus.SWITCHING_PROTOCOLS.code()
        && response
            .headers()
            .containsValue(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true);
  }

  private static void finishSpan(
      final ServerRequestContext serverContext,
      final Context storedContext,
      final AgentSpan span,
      final Future<?> future,
      final Channel channel) {
    if (!future.isSuccess()) {
      DECORATE.onError(span, future.cause());
      span.setHttpStatusCode(500);
    }
    finishSpan(serverContext, storedContext, span, channel);
  }

  private static void finishSpan(
      final ServerRequestContext serverContext,
      final Context storedContext,
      final AgentSpan span,
      final Channel channel) {
    try (final ContextScope ignored = storedContext.attach()) {
      tagResponseBody(channel, span);
      beforeFinish(serverContext, storedContext);
      span.finish(); // Finish the span manually since finishSpanOnClose was false
    }
  }

  private static void beforeFinish(
      final ServerRequestContext serverContext, final Context storedContext) {
    if (serverContext == null || !serverContext.isBeforeFinishCalled()) {
      if (serverContext != null) {
        serverContext.markBeforeFinishCalled();
      }
      DECORATE.beforeFinish(storedContext);
    }
  }

  private static void removeServerContext(
      final ChannelHandlerContext ctx, final ServerRequestContext serverContext) {
    if (serverContext == null) {
      ctx.channel().attr(CONTEXT_ATTRIBUTE_KEY).remove();
    } else {
      ServerRequestContext.remove(ctx.channel(), serverContext);
    }
  }

  static void tagResponseBody(Channel channel, AgentSpan span) {
    ByteArrayOutputStream buffer = channel.attr(RESPONSE_BODY_BUFFER_ATTRIBUTE_KEY).getAndSet(null);
    String encoding = channel.attr(RESPONSE_BODY_ENCODING_ATTRIBUTE_KEY).getAndSet(null);
    channel.attr(RESPONSE_BODY_REMAINING_BYTES_ATTRIBUTE_KEY).remove();
    if (buffer != null) {
      span.setTag(
          "response_body",
          new String(
              buffer.toByteArray(),
              HttpServerRequestTracingHandler.charset(encoding, StandardCharsets.UTF_8)));
    }
  }

  private static boolean initializeResponseBodyLength(
      Channel channel, HttpHeaders headers, int initialBytes) {
    String contentLength = headers.get(HttpHeaderNames.CONTENT_LENGTH);
    if (contentLength == null) {
      return false;
    }
    try {
      long remainingBytes = Long.parseLong(contentLength) - initialBytes;
      if (remainingBytes <= 0) {
        return true;
      }
      channel.attr(RESPONSE_BODY_REMAINING_BYTES_ATTRIBUTE_KEY).set(remainingBytes);
    } catch (NumberFormatException ignored) {
      return false;
    }
    return false;
  }

  static boolean isResponseBodyComplete(Channel channel, int writtenBytes) {
    Long remainingBytes = channel.attr(RESPONSE_BODY_REMAINING_BYTES_ATTRIBUTE_KEY).get();
    if (remainingBytes == null) {
      return false;
    }
    remainingBytes -= writtenBytes;
    if (remainingBytes <= 0) {
      channel.attr(RESPONSE_BODY_REMAINING_BYTES_ATTRIBUTE_KEY).remove();
      return true;
    }
    channel.attr(RESPONSE_BODY_REMAINING_BYTES_ATTRIBUTE_KEY).set(remainingBytes);
    return false;
  }

  private static void addResponseHeaderTag(AgentSpan span, HttpHeaders headers) {
    if (Config.get().isTracerHeaderEnabled()) {
      span.setTag("response_header", HttpServerRequestTracingHandler.formatHeaders(headers, false));
    }
  }
}
