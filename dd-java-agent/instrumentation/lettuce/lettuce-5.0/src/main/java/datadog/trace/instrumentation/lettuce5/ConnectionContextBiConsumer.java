package datadog.trace.instrumentation.lettuce5;

import datadog.trace.api.Config;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.util.MethodHandles;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.protocol.DefaultEndpoint;
import io.netty.channel.Channel;
import java.lang.invoke.MethodHandle;
import java.net.SocketAddress;
import java.util.function.BiConsumer;

public class ConnectionContextBiConsumer implements BiConsumer<StatefulConnection, Throwable> {

  private static final MethodHandle GET_CHANNEL =
      new MethodHandles(ConnectionContextBiConsumer.class.getClassLoader())
          .privateFieldGetter(DefaultEndpoint.class, "channel");

  private final DefaultEndpoint endpoint;
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
    this(redisURI, remoteAddress, capturePeerAddress, null, contextStore);
  }

  public ConnectionContextBiConsumer(
      RedisURI redisURI,
      SocketAddress remoteAddress,
      DefaultEndpoint endpoint,
      ContextStore<StatefulConnection, LettuceConnectionInfo> contextStore) {
    this(redisURI, remoteAddress, true, endpoint, contextStore);
  }

  private ConnectionContextBiConsumer(
      RedisURI redisURI,
      SocketAddress remoteAddress,
      boolean capturePeerAddress,
      DefaultEndpoint endpoint,
      ContextStore<StatefulConnection, LettuceConnectionInfo> contextStore) {
    this.endpoint = endpoint;
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
          new LettuceConnectionInfo(redisURI, connectedAddress(), capturePeerAddress));
    }
  }

  private SocketAddress connectedAddress() {
    // ConnectionFuture can hold an unresolved address. Read the connected channel once,
    // after connection establishment, without doing DNS or reflection for each command.
    if (capturePeerAddress
        && Config.get().isPeerHostnameFromConfigEnabled()
        && endpoint != null
        && GET_CHANNEL != null) {
      try {
        Channel channel = (Channel) GET_CHANNEL.invoke(endpoint);
        if (channel != null && channel.remoteAddress() != null) {
          return channel.remoteAddress();
        }
      } catch (Throwable ignored) {
        // Preserve the future's address if the endpoint layout is not supported.
      }
    }
    return remoteAddress;
  }
}
