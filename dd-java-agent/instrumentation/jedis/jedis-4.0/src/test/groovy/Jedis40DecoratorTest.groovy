import static datadog.trace.api.config.TraceInstrumentationConfig.PEER_HOSTNAME_FROM_CONFIG_ENABLED

import datadog.trace.agent.test.InstrumentationSpecification
import datadog.trace.bootstrap.FieldBackedContextStores
import datadog.trace.bootstrap.instrumentation.api.Tags
import redis.clients.jedis.Connection
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisClientDecorator

class Jedis40DecoratorTest extends InstrumentationSpecification {

  def "configured host does not override peer hostname by default"() {
    setup:
    def span = TEST_TRACER.startSpan("test", "redis.query")
    def connection = new Connection(new HostAndPort("10.225.132.12", 6379))
    int storeId = FieldBackedContextStores.getContextStoreId(Connection.name, String.name)
    FieldBackedContextStores.getContextStore(storeId).put(connection, "redis.example.com")

    when:
    JedisClientDecorator.DECORATE.onConnection(span, connection)

    then:
    span.getTag(Tags.PEER_HOSTNAME) == "10.225.132.12"

    cleanup:
    span.finish()
    TEST_WRITER.clear()
  }
}

class Jedis40DecoratorConfigHostForkedTest extends InstrumentationSpecification {

  @Override
  protected void configurePreAgent() {
    super.configurePreAgent()
    injectSysConfig(PEER_HOSTNAME_FROM_CONFIG_ENABLED, "true")
  }

  def "configured host overrides peer hostname when enabled"() {
    setup:
    def span = TEST_TRACER.startSpan("test", "redis.query")
    def connection = new Connection(new HostAndPort("10.225.132.12", 6379))
    int storeId = FieldBackedContextStores.getContextStoreId(Connection.name, String.name)
    FieldBackedContextStores.getContextStore(storeId).put(connection, "redis.example.com")

    when:
    JedisClientDecorator.DECORATE.onConnection(span, connection)

    then:
    span.getTag(Tags.PEER_HOSTNAME) == "redis.example.com"

    cleanup:
    span.finish()
    TEST_WRITER.clear()
  }
}
