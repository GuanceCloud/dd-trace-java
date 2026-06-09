package datadog.trace.instrumentation.vertx_redis_client;

import io.vertx.redis.client.RedisOptions;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RedisOptionsHostParser {
  private RedisOptionsHostParser() {}

  public static String configuredHost(final RedisOptions options) {
    if (options == null) {
      return null;
    }

    final List<String> endpoints = options.getEndpoints();
    if (endpoints != null && !endpoints.isEmpty()) {
      final Set<String> hosts = new HashSet<>();
      for (final String endpoint : endpoints) {
        final String host = parseHost(endpoint);
        if (host != null && !host.isEmpty()) {
          hosts.add(host);
        }
      }
      return hosts.size() == 1 ? hosts.iterator().next() : null;
    }

    return parseHost(options.getEndpoint());
  }

  private static String parseHost(final String endpoint) {
    if (endpoint == null || endpoint.isEmpty()) {
      return null;
    }

    try {
      final URI uri = new URI(endpoint.contains("://") ? endpoint : "redis://" + endpoint);
      return uri.getHost();
    } catch (final URISyntaxException ignored) {
      return null;
    }
  }
}
