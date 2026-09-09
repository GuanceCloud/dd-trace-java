import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan
import static datadog.trace.api.config.TraceInstrumentationConfig.DB_CLIENT_HOST_SPLIT_BY_HOST
import static datadog.trace.api.config.TraceInstrumentationConfig.PEER_HOSTNAME_FROM_CONFIG_ENABLED

import com.redis.testcontainers.RedisContainer
import datadog.trace.agent.test.InstrumentationSpecification
import datadog.trace.api.DDSpanTypes
import datadog.trace.bootstrap.instrumentation.api.Tags
import io.lettuce.core.RedisURI
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.output.StatusOutput
import io.lettuce.core.protocol.AsyncCommand
import io.lettuce.core.protocol.Command
import io.lettuce.core.protocol.CommandType
import java.util.concurrent.TimeUnit
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.ClusterClientOptions
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared
import spock.util.concurrent.PollingConditions

abstract class LettuceClusterHostTest extends InstrumentationSpecification {
  abstract boolean configHostEnabled()
  @Shared RedisContainer redis
  @Shared RedisClusterClient client
  @Shared String seedHost
  @Shared String nodeIp
  @Shared StatefulRedisClusterConnection<String, String> connection

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
    client = new NodeAddressClusterClient(
      RedisURI.create("redis://${seedHost}:${redis.firstMappedPort}"),
      RedisURI.create("redis://${nodeIp}:${redis.firstMappedPort}"))
    // Lettuce canonicalizes this one-node cluster to the seed hostname. Also exercise an IP connection.
    client.setOptions(ClusterClientOptions.builder().validateClusterNodeMembership(false).build())
    connection = client.connect()
  }

  def cleanupSpec() {
    connection?.close()
    client?.shutdown()
    redis?.stop()
  }

  def "topology refresh and node commands retain configured host and real IP"() {
    when:
    client.reloadPartitions()
    connection.sync().set("cluster-host-key", "value")
    def value = connection.sync().get("cluster-host-key")
    def node = connection.getConnection(nodeIp, redis.firstMappedPort)
    node.sync().set("node-key", "value")
    node.sync().clusterInfo()
    node.sync().ping()

    then:
    value == "value"
    new PollingConditions(timeout: 20).eventually {
      def spans = TEST_WRITER.flatten().findAll { it.getTag(Tags.COMPONENT).toString() == "redis-client" }
      assert spans.any { it.resourceName.toString() == "SET" }
      assert spans.any { it.resourceName.toString() == "GET" }
      assert spans.any { it.resourceName.toString().contains("CLUSTER") }
      spans.each {
        if (configHostEnabled()) {
          assert it.getTag(Tags.PEER_HOSTNAME) == seedHost
          assert it.serviceName == seedHost
          assert it.getTag(Tags.PEER_HOST_IPV4) == nodeIp
          assert it.getTag(Tags.PEER_PORT) == redis.firstMappedPort
        } else {
          assert it.getTag(Tags.PEER_HOSTNAME) in [seedHost, nodeIp]
          assert it.serviceName == it.getTag(Tags.PEER_HOSTNAME)
          assert it.getTag(Tags.PEER_HOST_IPV4) == null
          if (it.resourceName.toString() == "PING") {
            assert it.getTag(Tags.PEER_HOSTNAME) == nodeIp
          }
        }
      }
    }
  }

  def "endpoint writes do not replace an already configured cluster hostname"() {
    setup:
    def node = connection.getConnection(nodeIp, redis.firstMappedPort)
    node.sync().ping()
    def span = TEST_TRACER.startSpan("redis-client", "redis.query")
    span.setSpanType(DDSpanTypes.REDIS)
    span.setServiceName(seedHost)
    span.setTag(Tags.PEER_HOSTNAME, seedHost)
    def scope = activateSpan(span)

    when:
    // Write directly through the real endpoint, as deferred dispatch does after command decoration.
    def command = new AsyncCommand(new Command(CommandType.PING, new StatusOutput(StringCodec.UTF8)))
    node.channelWriter.write(command)
    def pong = command.get(10, TimeUnit.SECONDS)

    then:
    pong == "PONG"
    span.getTag(Tags.PEER_HOSTNAME) == (configHostEnabled() ? seedHost : nodeIp)
    span.serviceName == (configHostEnabled() ? seedHost : nodeIp)

    cleanup:
    scope?.close()
    span.finish()
  }
}

// Exercise the case where connection settings use a discovered IP while initialUris still
// identifies the application-configured cluster. This is Lettuce's protected configuration hook.
class NodeAddressClusterClient extends RedisClusterClient {
  private RedisURI nodeAddress

  NodeAddressClusterClient(RedisURI seed, RedisURI nodeAddress) {
    super(null, [seed])
    this.nodeAddress = nodeAddress
  }

  @Override
  protected RedisURI getFirstUri() {
    nodeAddress ?: super.getFirstUri()
  }
}

class LettuceClusterConfigHostForkedTest extends LettuceClusterHostTest {
  @Override boolean configHostEnabled() {
    true
  }
}

class LettuceClusterDefaultHostForkedTest extends LettuceClusterHostTest {
  @Override boolean configHostEnabled() {
    false
  }
}
