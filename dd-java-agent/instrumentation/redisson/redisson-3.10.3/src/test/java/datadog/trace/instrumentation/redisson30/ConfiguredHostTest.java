package datadog.trace.instrumentation.redisson30;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.redisson.client.RedisClientConfig;
import org.redisson.config.Config;

class ConfiguredHostTest {
  @Test
  void clientAddressSupportsTheRuntimeRedissonVersion() {
    RedisClientConfig config = new RedisClientConfig().setAddress("redis://localhost:6379");
    assertEquals("localhost", RedisClientHostParser.hostFromConfig(config));
  }

  @Test
  void onlyHostIsExtractedFromAddresses() {
    assertEquals(
        "redis.example.com",
        RedisClientHostParser.hostFrom(
            URI.create("rediss://user:secret@redis.example.com:6380/2")));
    assertEquals(
        "redis.example.com",
        RedisClientHostParser.hostFrom("rediss://user:secret@redis.example.com:6380/2"));
    assertEquals("[::1]", RedisClientHostParser.hostFrom("redis://[::1]:6379"));
    assertNull(RedisClientHostParser.hostFrom(null));
    assertNull(RedisClientHostParser.hostFrom("not a URI"));
    assertNull(RedisClientHostParser.hostFrom(new Object()));
  }

  @Test
  void clusterUsesFirstConfiguredSeedRatherThanDiscoveredNodes() {
    Config config = new Config();
    config
        .useClusterServers()
        .addNodeAddress("redis://cluster.example.com:6379", "redis://other.example.com:6379");
    assertEquals("cluster.example.com", ConnectionManagerHost.configuredHost(config));
  }

  @Test
  void replicatedAndSentinelConfigurationsHaveStableHosts() {
    Config replicated = new Config();
    replicated.useReplicatedServers().addNodeAddress("redis://replicated.example.com:6379");
    assertEquals("replicated.example.com", ConnectionManagerHost.configuredHost(replicated));
    Config sentinel = new Config();
    sentinel
        .useSentinelServers()
        .setMasterName("master")
        .addSentinelAddress("redis://sentinel.example.com:26379");
    assertEquals("sentinel.example.com", ConnectionManagerHost.configuredHost(sentinel));
  }

  @Test
  void missingSeedsAndNonDiscoveryModesKeepTheClientAddress() {
    assertNull(ConnectionManagerHost.configuredHost(null));
    Config empty = new Config();
    empty.useClusterServers();
    assertNull(ConnectionManagerHost.configuredHost(empty));
    Config single = new Config();
    single.useSingleServer().setAddress("redis://single.example.com:6379");
    assertNull(ConnectionManagerHost.configuredHost(single));
    Config masterSlave = new Config();
    masterSlave.useMasterSlaveServers().setMasterAddress("redis://master.example.com:6379");
    assertNull(ConnectionManagerHost.configuredHost(masterSlave));
  }
}
