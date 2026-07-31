package datadog.trace.instrumentation.netty40.server;

import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromContext;
import static datadog.trace.instrumentation.netty40.AttributeKeys.CHANNEL_ID;
import static datadog.trace.instrumentation.netty40.AttributeKeys.CONTEXT_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty40.AttributeKeys.REQUEST_METHOD_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty40.AttributeKeys.REQUEST_URI_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty40.AttributeKeys.RESPONSE_BODY_BUFFER_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty40.AttributeKeys.RESPONSE_BODY_ENCODING_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty40.AttributeKeys.RESPONSE_BODY_REMAINING_BYTES_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty40.AttributeKeys.WEBSOCKET_SENDER_HANDLER_CONTEXT;
import static datadog.trace.instrumentation.netty40.server.NettyHttpServerDecorator.DECORATE;

import datadog.context.Context;
import datadog.context.ContextScope;
import datadog.trace.api.Config;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.websocket.HandlerContext;
import datadog.trace.util.RandomUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

@ChannelHandler.Sharable
public class HttpServerResponseTracingHandler extends ChannelOutboundHandlerAdapter {
  public static HttpServerResponseTracingHandler INSTANCE = new HttpServerResponseTracingHandler();
  private static final String UPGRADE_HEADER = "upgrade";

  @Override
  public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise prm) {
    final Context storedContext = ctx.channel().attr(CONTEXT_ATTRIBUTE_KEY).get();
    final AgentSpan span = spanFromContext(storedContext);
    if (span == null) {
      ctx.write(msg, prm);
      return;
    }

    if (msg instanceof HttpResponse) {
      writeResponse(ctx, msg, prm, storedContext, span, (HttpResponse) msg);
      return;
    }

    if (msg instanceof HttpContent
        && ctx.channel().attr(RESPONSE_BODY_BUFFER_ATTRIBUTE_KEY).get() != null) {
      writeResponseContent(ctx, msg, prm, storedContext, span, (HttpContent) msg);
      return;
    }

    if (msg instanceof ByteBuf
        && ctx.channel().attr(RESPONSE_BODY_BUFFER_ATTRIBUTE_KEY).get() != null) {
      writeResponseBytes(ctx, msg, prm, storedContext, span, (ByteBuf) msg);
      return;
    }

    ctx.write(msg, prm);
  }

  private static void writeResponse(
      ChannelHandlerContext ctx,
      Object msg,
      ChannelPromise prm,
      Context storedContext,
      AgentSpan span,
      HttpResponse response) {
    try (final ContextScope scope = storedContext.attach()) {
      addResponseHeaderTag(span, response.headers());
      final boolean finalResponse = isFinalResponse(response);
      final boolean captureResponseBody =
          finalResponse && shouldCaptureResponseBody(ctx, response.headers());
      boolean responseBodyComplete = false;
      if (captureResponseBody) {
        ctx.channel().attr(RESPONSE_BODY_BUFFER_ATTRIBUTE_KEY).set(new ByteArrayOutputStream());
        ctx.channel()
            .attr(RESPONSE_BODY_ENCODING_ATTRIBUTE_KEY)
            .set(
                HttpServerRequestTracingHandler.charsetName(
                    response.headers().get(HttpHeaders.Names.CONTENT_TYPE),
                    Config.get().getTracerResponseBodyEncoding()));
        if (msg instanceof HttpContent) {
          ByteBuf content = ((HttpContent) msg).content();
          HttpServerRequestTracingHandler.append(
              ctx.channel().attr(RESPONSE_BODY_BUFFER_ATTRIBUTE_KEY).get(), content);
          responseBodyComplete =
              initializeResponseBodyLength(
                  ctx.channel(), response.headers(), content.readableBytes());
        } else {
          responseBodyComplete = initializeResponseBodyLength(ctx.channel(), response.headers(), 0);
        }
      }

      try {
        ctx.write(msg, prm);
      } catch (final Throwable throwable) {
        finishOnError(ctx, span, throwable);
        throw throwable;
      }
      final boolean isWebsocketUpgrade =
          response.getStatus() == HttpResponseStatus.SWITCHING_PROTOCOLS
              && "websocket".equals(response.headers().get(UPGRADE_HEADER));
      if (isWebsocketUpgrade) {
        String channelId =
            ctx.channel()
                .attr(CHANNEL_ID)
                .setIfAbsent(RandomUtils.randomUUID().toString().substring(0, 8));
        ctx.channel()
            .attr(WEBSOCKET_SENDER_HANDLER_CONTEXT)
            .set(new HandlerContext.Sender(span, channelId));
      }
      if (finalResponse) {
        DECORATE.onResponse(span, response);
      }
      if (finalResponse
          && (!captureResponseBody
              || msg instanceof LastHttpContent
              || responseBodyComplete
              || isEmptyResponse(ctx, response))) {
        finishResponse(ctx, scope.context(), span);
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
      boolean responseBodyComplete =
          isResponseBodyComplete(ctx.channel(), content.content().readableBytes());
      try {
        ctx.write(msg, prm);
      } catch (final Throwable throwable) {
        finishOnError(ctx, span, throwable);
        throw throwable;
      }
      if (msg instanceof LastHttpContent || responseBodyComplete) {
        finishResponse(ctx, scope.context(), span);
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
      boolean responseBodyComplete = isResponseBodyComplete(ctx.channel(), content.readableBytes());
      try {
        ctx.write(msg, prm);
      } catch (final Throwable throwable) {
        finishOnError(ctx, span, throwable);
        throw throwable;
      }
      if (responseBodyComplete) {
        finishResponse(ctx, scope.context(), span);
      }
    }
  }

  private static boolean shouldCaptureResponseBody(ChannelHandlerContext ctx, HttpHeaders headers) {
    return Config.get().isTracerResponseBodyEnabled()
        && isResponseBodyAllowed(ctx.channel().attr(REQUEST_URI_ATTRIBUTE_KEY).get())
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

  private static boolean isEmptyResponse(ChannelHandlerContext ctx, HttpResponse response) {
    if ("HEAD".equalsIgnoreCase(ctx.channel().attr(REQUEST_METHOD_ATTRIBUTE_KEY).get())) {
      return true;
    }
    int statusCode = response.getStatus().code();
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

  private static void finishOnError(
      ChannelHandlerContext ctx, AgentSpan span, Throwable throwable) {
    ctx.channel().attr(RESPONSE_BODY_BUFFER_ATTRIBUTE_KEY).remove();
    ctx.channel().attr(RESPONSE_BODY_ENCODING_ATTRIBUTE_KEY).remove();
    ctx.channel().attr(RESPONSE_BODY_REMAINING_BYTES_ATTRIBUTE_KEY).remove();
    DECORATE.onError(span, throwable);
    span.setHttpStatusCode(500);
    span.finish();
    ctx.channel().attr(CONTEXT_ATTRIBUTE_KEY).remove();
  }

  private static boolean initializeResponseBodyLength(
      Channel channel, HttpHeaders headers, int initialBytes) {
    String contentLength = headers.get(HttpHeaders.Names.CONTENT_LENGTH);
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

  private static void finishResponse(ChannelHandlerContext ctx, Context context, AgentSpan span) {
    tagResponseBody(ctx.channel(), span);
    DECORATE.beforeFinish(context);
    ctx.channel().attr(CONTEXT_ATTRIBUTE_KEY).remove();
    span.finish(); // Finish the span manually since finishSpanOnClose was false
  }

  private static void addResponseHeaderTag(AgentSpan span, HttpHeaders headers) {
    if (Config.get().isTracerHeaderEnabled()) {
      span.setTag("response_header", HttpServerRequestTracingHandler.formatHeaders(headers, false));
    }
  }
}
