package datadog.trace.instrumentation.lettuce5;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.bootstrap.instrumentation.api.ServiceNameSources.DB_CLIENT_SPLIT_BY_HOST;

import datadog.trace.api.Config;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.InternalSpanTypes;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import io.netty.channel.Channel;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import net.bytebuddy.asm.Advice;

public class LettuceDefaultEndpointAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AgentSpan onEnter(
      @Advice.This Object obj, @Advice.FieldValue("channel") Channel channel) {
    AgentSpan span = activeSpan();
    if (span == null
        || !String.valueOf(InternalSpanTypes.REDIS).equals(String.valueOf(span.getSpanType()))) {
      return span;
    }
    if (channel == null) {
      return span;
    }

    SocketAddress socketAddress = channel.remoteAddress();
    if (socketAddress instanceof InetSocketAddress) {
      InetSocketAddress inetSocketAddress = (InetSocketAddress) socketAddress;
      final String hostName = inetSocketAddress.getHostString();
      if (hostName != null) {
        span.setTag(Tags.PEER_HOSTNAME, hostName);
        if (Config.get().isDbClientSplitByHost()) {
          span.setServiceName(hostName, DB_CLIENT_SPLIT_BY_HOST);
        }
      }
      span.setTag(Tags.PEER_PORT, inetSocketAddress.getPort());
    }
    return span;
  }
}
