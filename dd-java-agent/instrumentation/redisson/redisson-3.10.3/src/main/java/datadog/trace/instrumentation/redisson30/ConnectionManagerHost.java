package datadog.trace.instrumentation.redisson30;

import datadog.trace.util.MethodHandles;
import java.lang.invoke.MethodHandle;
import java.util.List;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.ReplicatedServersConfig;
import org.redisson.config.SentinelServersConfig;

/**
 * Reads the original seed configuration, before topology discovery replaces hosts with node IPs.
 */
public final class ConnectionManagerHost {
  private static final MethodHandles HANDLES =
      new MethodHandles(ConnectionManagerHost.class.getClassLoader());
  private static final MethodHandle GET_CONFIG =
      HANDLES.method("org.redisson.connection.MasterSlaveConnectionManager", "getCfg");
  private static final MethodHandle GET_SERVICE_MANAGER =
      GET_CONFIG == null
          ? HANDLES.method(
              "org.redisson.connection.MasterSlaveConnectionManager", "getServiceManager")
          : null;
  private static final MethodHandle GET_SERVICE_CONFIG =
      GET_CONFIG == null
          ? HANDLES.method("org.redisson.connection.ServiceManager", "getCfg")
          : null;
  private static final MethodHandle GET_CLUSTER =
      HANDLES.method(Config.class, "getClusterServersConfig");
  private static final MethodHandle GET_REPLICATED =
      HANDLES.method(Config.class, "getReplicatedServersConfig");
  private static final MethodHandle GET_SENTINEL =
      HANDLES.method(Config.class, "getSentinelServersConfig");

  private ConnectionManagerHost() {}

  public static String getHost(Object manager) {
    try {
      Object config;
      if (GET_CONFIG != null) {
        config = GET_CONFIG.invoke(manager);
      } else if (GET_SERVICE_MANAGER != null && GET_SERVICE_CONFIG != null) {
        config = GET_SERVICE_CONFIG.invoke(GET_SERVICE_MANAGER.invoke(manager));
      } else {
        return null;
      }
      return configuredHost((Config) config);
    } catch (Throwable ignored) {
      return null;
    }
  }

  static String configuredHost(Config config) {
    try {
      if (config == null) {
        return null;
      }
      ClusterServersConfig cluster = (ClusterServersConfig) GET_CLUSTER.invoke(config);
      if (cluster != null) {
        return firstHost(cluster.getNodeAddresses());
      }
      ReplicatedServersConfig replicated = (ReplicatedServersConfig) GET_REPLICATED.invoke(config);
      if (replicated != null) {
        return firstHost(replicated.getNodeAddresses());
      }
      SentinelServersConfig sentinel = (SentinelServersConfig) GET_SENTINEL.invoke(config);
      if (sentinel != null) {
        return firstHost(sentinel.getSentinelAddresses());
      }
      // Single-server and explicitly configured master/slave clients keep their own address.
    } catch (Throwable ignored) {
      // Unsupported versions must retain socket-address naming and must not affect the application.
    }
    return null;
  }

  private static String firstHost(List<?> addresses) {
    if (addresses != null) {
      for (Object address : addresses) {
        String host = RedisClientHostParser.hostFrom(address);
        if (host != null && !host.isEmpty()) {
          return host;
        }
      }
    }
    return null;
  }
}
