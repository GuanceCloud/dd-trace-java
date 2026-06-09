package datadog.trace.instrumentation.lettuce5;

import io.lettuce.core.RedisURI;
import java.net.SocketAddress;

public final class LettuceConnectionInfo {
  private final RedisURI redisURI;
  private final SocketAddress remoteAddress;
  private final boolean capturePeerAddress;

  public LettuceConnectionInfo(
      RedisURI redisURI, SocketAddress remoteAddress, boolean capturePeerAddress) {
    this.redisURI = redisURI;
    this.remoteAddress = remoteAddress;
    this.capturePeerAddress = capturePeerAddress;
  }

  public RedisURI getRedisURI() {
    return redisURI;
  }

  public SocketAddress getRemoteAddress() {
    return remoteAddress;
  }

  public boolean isCapturePeerAddress() {
    return capturePeerAddress;
  }
}
