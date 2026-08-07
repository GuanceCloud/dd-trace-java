package datadog.trace.instrumentation.lettuce5;

import datadog.trace.bootstrap.ContextStore;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulConnection;
import java.net.SocketAddress;
import java.util.function.BiConsumer;

public class ConnectionContextBiConsumer implements BiConsumer<StatefulConnection, Throwable> {

  private final RedisURI redisURI;
  private final SocketAddress remoteAddress;
  private final boolean capturePeerAddress;
  private final ContextStore<StatefulConnection, LettuceConnectionInfo> contextStore;

  public ConnectionContextBiConsumer(
      RedisURI redisURI, ContextStore<StatefulConnection, LettuceConnectionInfo> contextStore) {
    this(redisURI, null, false, contextStore);
  }

  public ConnectionContextBiConsumer(
      RedisURI redisURI,
      SocketAddress remoteAddress,
      boolean capturePeerAddress,
      ContextStore<StatefulConnection, LettuceConnectionInfo> contextStore) {
    this.redisURI = redisURI;
    this.remoteAddress = remoteAddress;
    this.capturePeerAddress = capturePeerAddress;
    this.contextStore = contextStore;
  }

  @Override
  public void accept(StatefulConnection statefulConnection, Throwable throwable) {
    if (statefulConnection != null) {
      contextStore.put(
          statefulConnection,
          new LettuceConnectionInfo(redisURI, remoteAddress, capturePeerAddress));
    }
  }
}
