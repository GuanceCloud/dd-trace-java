package datadog.trace.instrumentation.netty41.client;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.noopSpan;
import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromContext;
import static datadog.trace.instrumentation.netty41.AttributeKeys.CLIENT_PARENT_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty41.AttributeKeys.CLIENT_RESPONSE_STREAM_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty41.AttributeKeys.CONTEXT_ATTRIBUTE_KEY;
import static datadog.trace.instrumentation.netty41.client.NettyHttpClientDecorator.DECORATE;

import datadog.context.Context;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.Attribute;

@ChannelHandler.Sharable
public class HttpClientResponseTracingHandler extends ChannelInboundHandlerAdapter {
  public static final HttpClientResponseTracingHandler INSTANCE =
      new HttpClientResponseTracingHandler();

  @Override
  public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
    final Attribute<AgentSpan> parentAttr = ctx.channel().attr(CLIENT_PARENT_ATTRIBUTE_KEY);
    parentAttr.setIfAbsent(noopSpan());
    final AgentSpan parent = parentAttr.get();
    final Context storedContext = ctx.channel().attr(CONTEXT_ATTRIBUTE_KEY).get();
    final AgentSpan span = spanFromContext(storedContext);

    // Set parent context back to maintain the same functionality as getAndSet(parent)
    if (storedContext != null) {
      ctx.channel().attr(CONTEXT_ATTRIBUTE_KEY).set(storedContext.with(parent));
    }

    if (span != null) {
      final boolean finishSpan =
          msg instanceof HttpResponse
              && (!HttpResponseStatus.SWITCHING_PROTOCOLS.equals(((HttpResponse) msg).status())
                  || "websocket"
                      .equals(((HttpResponse) msg).headers().get(HttpHeaderNames.UPGRADE)));
      if (finishSpan) {
        try (final AgentScope scope = activateSpan(span)) {
          final HttpResponse response = (HttpResponse) msg;
          DECORATE.onResponse(span, response);
          final NettyClientResponseStream stream =
              NettyClientResponseStream.startIfSse(span, response);
          if (stream != null) {
            ctx.channel().attr(CLIENT_RESPONSE_STREAM_ATTRIBUTE_KEY).set(stream);
          }
          DECORATE.beforeFinish(span);
          span.finish();
        }
      } else {
        if (storedContext != null) {
          ctx.channel().attr(CONTEXT_ATTRIBUTE_KEY).set(storedContext);
        }
      }
    }

    if (msg instanceof HttpObject) {
      handleResponseStream(ctx, msg);
    }

    // We want the callback in the scope of the parent, not the client span
    try (final AgentScope scope = activateSpan(parent)) {
      ctx.fireChannelRead(msg);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    final Attribute<AgentSpan> parentAttr = ctx.channel().attr(CLIENT_PARENT_ATTRIBUTE_KEY);
    parentAttr.setIfAbsent(noopSpan());
    final AgentSpan parent = parentAttr.get();
    final Context storedContext = ctx.channel().attr(CONTEXT_ATTRIBUTE_KEY).get();
    final AgentSpan span = spanFromContext(storedContext);

    // Set parent context back to maintain the same functionality as getAndSet(parent)
    if (storedContext != null) {
      ctx.channel().attr(CONTEXT_ATTRIBUTE_KEY).set(storedContext.with(parent));
    }

    if (span != null) {
      // If an exception is passed to this point, it likely means it was unhandled and the
      // client span won't be finished with a proper response, so we should finish the span here.
      try (final AgentScope scope = activateSpan(span)) {
        DECORATE.onError(span, cause);
        DECORATE.beforeFinish(span);
        span.finish();
      }
    }
    finishResponseStreamWithError(ctx, cause);
    // We want the callback in the scope of the parent, not the client span
    try (final AgentScope scope = activateSpan(parent)) {
      super.exceptionCaught(ctx, cause);
    }
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    final Attribute<AgentSpan> parentAttr = ctx.channel().attr(CLIENT_PARENT_ATTRIBUTE_KEY);
    parentAttr.setIfAbsent(noopSpan());
    final AgentSpan parent = parentAttr.get();
    final Context storedContext = ctx.channel().attr(CONTEXT_ATTRIBUTE_KEY).get();
    final AgentSpan span = spanFromContext(storedContext);

    // Set parent context back to maintain the same functionality  as getAndSet(parent)
    if (storedContext != null) {
      ctx.channel().attr(CONTEXT_ATTRIBUTE_KEY).set(storedContext.with(parent));
    }

    if (span != null && span != parent) {
      try (final AgentScope scope = activateSpan(span)) {
        DECORATE.beforeFinish(span);
        span.finish();
      }
    }
    finishResponseStream(ctx);
    // We want the callback in the scope of the parent, not the client span
    try (final AgentScope scope = activateSpan(parent)) {
      super.channelInactive(ctx);
    }
  }

  private static void handleResponseStream(final ChannelHandlerContext ctx, final Object msg) {
    final NettyClientResponseStream stream = getResponseStream(ctx);
    if (stream == null) {
      return;
    }
    if (msg instanceof HttpContent) {
      stream.onChunk();
    }
    if (msg instanceof LastHttpContent) {
      finishResponseStream(ctx);
    }
  }

  private static NettyClientResponseStream getResponseStream(final ChannelHandlerContext ctx) {
    final Object stream = ctx.channel().attr(CLIENT_RESPONSE_STREAM_ATTRIBUTE_KEY).get();
    return stream instanceof NettyClientResponseStream ? (NettyClientResponseStream) stream : null;
  }

  private static void finishResponseStream(final ChannelHandlerContext ctx) {
    final NettyClientResponseStream stream = getResponseStream(ctx);
    if (stream != null) {
      ctx.channel().attr(CLIENT_RESPONSE_STREAM_ATTRIBUTE_KEY).remove();
      stream.finish();
    }
  }

  private static void finishResponseStreamWithError(
      final ChannelHandlerContext ctx, final Throwable cause) {
    final NettyClientResponseStream stream = getResponseStream(ctx);
    if (stream != null) {
      ctx.channel().attr(CLIENT_RESPONSE_STREAM_ATTRIBUTE_KEY).remove();
      stream.finishWithError(cause);
    }
  }
}
