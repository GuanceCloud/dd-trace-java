import static datadog.trace.api.config.TraceInstrumentationConfig.DB_CLIENT_HOST_SPLIT_BY_HOST
import static datadog.trace.api.config.TraceInstrumentationConfig.PEER_HOSTNAME_FROM_CONFIG_ENABLED

import com.redis.testcontainers.RedisContainer
import datadog.trace.agent.test.InstrumentationSpecification
import datadog.trace.bootstrap.instrumentation.api.Tags
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.resolver.DefaultAddressResolverGroup
import org.redisson.Redisson
import org.redisson.client.RedisClient
import org.redisson.client.RedisClientConfig
import org.redisson.client.codec.StringCodec
import org.redisson.client.protocol.RedisCommands
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared
import spock.util.concurrent.PollingConditions

abstract class RedissonClusterHostTest extends InstrumentationSpecification {
  @Shared RedisContainer redis
  @Shared RedissonClient client
  @Shared String seedHost
  @Shared String nodeIp

  abstract boolean configHostEnabled()

  @Override
  protected void configurePreAgent() {
    super.configurePreAgent()
    injectSysConfig(PEER_HOSTNAME_FROM_CONFIG_ENABLED, configHostEnabled().toString())
    injectSysConfig(DB_CLIENT_HOST_SPLIT_BY_HOST, "true")
  }

  def setupSpec() {
    redis = new RedisContainer(DockerImageName.parse("redis:6.2.6"))
      .withCommand("sh", "-c", "echo ready; sleep infinity")
      .waitingFor(Wait.forLogMessage("ready\\n", 1))
    redis.start()
    seedHost = redis.host
    nodeIp = InetAddress.getByName(seedHost).hostAddress
    assert redis.execInContainer("redis-server", "--daemonize", "yes", "--cluster-enabled", "yes",
    "--cluster-config-file", "/tmp/nodes.conf", "--cluster-announce-ip", nodeIp,
    "--cluster-announce-port", redis.firstMappedPort.toString()).exitCode == 0
    new PollingConditions(timeout: 10).eventually {
      assert redis.execInContainer("redis-cli", "ping").stdout.trim() == "PONG"
    }
    assert redis.execInContainer("sh", "-c", "redis-cli cluster addslots \$(seq 0 16383)").stdout.trim() == "OK"
    new PollingConditions(timeout: 10).eventually {
      assert redis.execInContainer("redis-cli", "cluster", "info").stdout.contains("cluster_state:ok")
    }
    client = newClient(seedHost)
  }

  RedissonClient newClient(String host) {
    def config = new Config().setThreads(2).setNettyThreads(2)
    config.useClusterServers()
      .addNodeAddress("redis://${host}:${redis.firstMappedPort}")
      .setMasterConnectionMinimumIdleSize(1).setMasterConnectionPoolSize(2)
      .setSlaveConnectionMinimumIdleSize(1).setSlaveConnectionPoolSize(2)
      .setSubscriptionConnectionMinimumIdleSize(1).setSubscriptionConnectionPoolSize(2)
    Redisson.create(config)
  }

  def cleanupSpec() {
    client?.shutdown()
    redis?.stop()
  }

  def "discovered node commands use the seed host only when enabled"() {
    when:
    client.getBucket("cluster-host-key").set("value")
    def value = client.getBucket("cluster-host-key").get()

    then:
    value == "value"
    assertCommandHosts(["SET", "GET"], configHostEnabled() ? seedHost : nodeIp)
  }

  def "direct clients retain configured host across address API versions"() {
    setup:
    def group = new NioEventLoopGroup(1)
    def config = new RedisClientConfig().setGroup(group)
      .setResolverGroup(DefaultAddressResolverGroup.INSTANCE)
      .setAddress("redis://${seedHost}:${redis.firstMappedPort}")
    // Pre-resolve the socket to an IP while preserving the configured URI. Groovy selects
    // the URI or RedisURI overload supplied by the Redisson version under test.
    config.setAddress(new InetSocketAddress(nodeIp, redis.firstMappedPort), config.address)
    def direct = RedisClient.create(config)
    def connection = direct.connect()
    TEST_WRITER.clear()

    when:
    def pong = connection.sync(StringCodec.INSTANCE, RedisCommands.PING)

    then:
    pong == "PONG"
    assertCommandHosts(["PING"], configHostEnabled() ? seedHost : direct.addr.hostString)

    cleanup:
    connection?.closeAsync()
    direct?.shutdown()
    group.shutdownGracefully().syncUninterruptibly()
  }

  def "batched node commands retain the seed host and real IP"() {
    when:
    def batch = client.createBatch()
    batch.getBucket("cluster-host-key").setAsync("batch-value")
    batch.getBucket("cluster-host-key").getAsync()
    batch.execute()

    then:
    assertCommandHosts(["SET;GET"], configHostEnabled() ? seedHost : nodeIp)
  }

  def "clients sharing nodes keep independent configured hosts"() {
    setup:
    def other = newClient(nodeIp)
    TEST_WRITER.clear()

    when:
    other.getBucket("other-cluster-host-key").set("other-value")

    then:
    assertCommandHosts(["SET"], nodeIp)

    when:
    TEST_WRITER.clear()
    client.getBucket("cluster-host-key").set("value")

    then:
    assertCommandHosts(["SET"], configHostEnabled() ? seedHost : nodeIp)

    cleanup:
    other?.shutdown()
  }

  void assertCommandHosts(List<String> commands, String host) {
    new PollingConditions(timeout: 20).eventually {
      assert commands.every { command ->
        TEST_WRITER.flatten().any {
          it.resourceName.toString() == command
        }
      }
    }
    def spans = TEST_WRITER.flatten().findAll { commands.contains(it.resourceName.toString()) }
    assert spans.size() == commands.size()
    spans.each {
      assert it.getTag(Tags.PEER_HOSTNAME) == host
      assert it.serviceName == host
      assert it.getTag(Tags.PEER_HOST_IPV4) == nodeIp
      assert it.getTag(Tags.PEER_PORT) == redis.firstMappedPort
    }
  }
}

class RedissonClusterConfigHostForkedTest extends RedissonClusterHostTest {
  @Override boolean configHostEnabled() {
    true
  }
}

class RedissonClusterSocketHostForkedTest extends RedissonClusterHostTest {
  @Override boolean configHostEnabled() {
    false
  }
}
