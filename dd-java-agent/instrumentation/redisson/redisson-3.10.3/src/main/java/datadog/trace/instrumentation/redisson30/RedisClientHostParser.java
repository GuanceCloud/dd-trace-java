package datadog.trace.instrumentation.redisson30;

import java.net.URI;

public final class RedisClientHostParser {
  private RedisClientHostParser() {}

  public static String hostFrom(final URI uri) {
    return uri == null ? null : uri.getHost();
  }
}
