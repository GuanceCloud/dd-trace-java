package datadog.trace.instrumentation.redisson30;

import datadog.trace.util.MethodHandles;
import java.lang.invoke.MethodHandle;
import java.net.URI;
import org.redisson.client.RedisClientConfig;

public final class RedisClientHostParser {
  // getAddress changed from URI to RedisURI. Do not link either return type into advice.
  private static final MethodHandle GET_ADDRESS =
      new MethodHandles(RedisClientHostParser.class.getClassLoader())
          .method(RedisClientConfig.class, "getAddress");
  private static final MethodHandle GET_HOST =
      new MethodHandles(RedisClientHostParser.class.getClassLoader())
          .method("org.redisson.misc.RedisURI", "getHost");

  private RedisClientHostParser() {}

  public static String hostFromConfig(final RedisClientConfig config) {
    try {
      return GET_ADDRESS == null ? null : hostFrom(GET_ADDRESS.invoke(config));
    } catch (Throwable ignored) {
      return null;
    }
  }

  public static String hostFrom(final Object address) {
    if (address == null) {
      return null;
    }
    try {
      if (address instanceof URI) {
        return ((URI) address).getHost();
      }
      if (address instanceof String) {
        return URI.create((String) address).getHost();
      }
      return GET_HOST == null ? null : (String) GET_HOST.invoke(address);
    } catch (Throwable ignored) {
      return null;
    }
  }
}
