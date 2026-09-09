package datadog.trace.instrumentation.lettuce5;

import datadog.trace.api.Config;
import datadog.trace.bootstrap.InstrumentationContext;
import io.lettuce.core.ConnectionFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.protocol.DefaultEndpoint;
import java.net.SocketAddress;
import net.bytebuddy.asm.Advice;

public class ClusterConnectionFutureAdvice {

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void afterConnect(
      @Advice.AllArguments final Object[] args,
      @Advice.FieldValue("initialUris") final Iterable<RedisURI> initialUris,
      @Advice.Return(readOnly = false) ConnectionFuture connectionFuture) {
    if (connectionFuture == null) {
      return;
    }

    RedisURI redisURI = null;
    DefaultEndpoint endpoint = null;
    for (Object arg : args) {
      if (arg instanceof RedisURI) {
        redisURI = (RedisURI) arg;
      } else if (arg instanceof DefaultEndpoint) {
        endpoint = (DefaultEndpoint) arg;
      }
    }
    if (Config.get().isPeerHostnameFromConfigEnabled() && initialUris != null) {
      for (RedisURI seed : initialUris) {
        if (seed != null && seed.getHost() != null && !seed.getHost().isEmpty()) {
          redisURI = seed;
          break;
        }
      }
    }
    if (redisURI == null) {
      return;
    }

    SocketAddress remoteAddress = connectionFuture.getRemoteAddress();
    connectionFuture =
        connectionFuture.whenComplete(
            new ConnectionContextBiConsumer(
                redisURI,
                remoteAddress,
                endpoint,
                InstrumentationContext.get(StatefulConnection.class, LettuceConnectionInfo.class)));
  }
}
