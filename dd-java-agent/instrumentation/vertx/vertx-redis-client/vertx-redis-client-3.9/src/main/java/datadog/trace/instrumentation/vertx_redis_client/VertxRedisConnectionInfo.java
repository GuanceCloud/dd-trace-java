package datadog.trace.instrumentation.vertx_redis_client;

import io.vertx.core.net.SocketAddress;

public final class VertxRedisConnectionInfo {
  private final SocketAddress socketAddress;
  private final String configuredHost;

  public VertxRedisConnectionInfo(final SocketAddress socketAddress, final String configuredHost) {
    this.socketAddress = socketAddress;
    this.configuredHost = configuredHost;
  }

  public SocketAddress getSocketAddress() {
    return socketAddress;
  }

  public String getConfiguredHost() {
    return configuredHost;
  }
}
