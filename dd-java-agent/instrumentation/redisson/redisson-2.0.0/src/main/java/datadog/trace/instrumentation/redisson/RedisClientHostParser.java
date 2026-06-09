package datadog.trace.instrumentation.redisson;

import java.net.URI;

public final class RedisClientHostParser {
  private RedisClientHostParser() {}

  public static String hostFrom(final Object[] args) {
    if (args == null) {
      return null;
    }
    for (Object arg : args) {
      if (arg instanceof URI) {
        return hostFrom((URI) arg);
      }
    }
    for (Object arg : args) {
      if (arg instanceof String) {
        return hostFrom((String) arg);
      }
    }
    return null;
  }

  public static String hostFrom(final URI uri) {
    return uri == null ? null : uri.getHost();
  }

  public static String hostFrom(final String address) {
    if (address == null || address.isEmpty()) {
      return null;
    }
    if (address.indexOf("://") > -1) {
      try {
        String host = URI.create(address).getHost();
        if (host != null && !host.isEmpty()) {
          return host;
        }
      } catch (IllegalArgumentException ignored) {
      }
    }
    return address;
  }
}
