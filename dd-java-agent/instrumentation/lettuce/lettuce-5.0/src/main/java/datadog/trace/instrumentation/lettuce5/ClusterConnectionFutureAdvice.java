package datadog.trace.instrumentation.lettuce5;

import datadog.trace.bootstrap.InstrumentationContext;
import io.lettuce.core.ConnectionFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulConnection;
import java.net.SocketAddress;
import net.bytebuddy.asm.Advice;

public class ClusterConnectionFutureAdvice {

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void afterConnect(
      @Advice.AllArguments final Object[] args,
      @Advice.Return(readOnly = false) ConnectionFuture connectionFuture) {
    if (connectionFuture == null) {
      return;
    }

    RedisURI redisURI = null;
    for (Object arg : args) {
      if (arg instanceof RedisURI) {
        redisURI = (RedisURI) arg;
        break;
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
                true,
                InstrumentationContext.get(StatefulConnection.class, LettuceConnectionInfo.class)));
  }
}
