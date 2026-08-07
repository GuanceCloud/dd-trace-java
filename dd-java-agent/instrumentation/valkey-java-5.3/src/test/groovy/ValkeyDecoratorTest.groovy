import static datadog.trace.api.config.TraceInstrumentationConfig.PEER_HOSTNAME_FROM_CONFIG_ENABLED

import datadog.trace.agent.test.InstrumentationSpecification
import datadog.trace.bootstrap.FieldBackedContextStores
import datadog.trace.bootstrap.instrumentation.api.Tags
import io.valkey.Connection
import io.valkey.HostAndPort
import io.valkey.ValkeyClientDecorator

class ValkeyDecoratorTest extends InstrumentationSpecification {

  def "configured host does not override peer hostname by default"() {
    setup:
    def span = TEST_TRACER.startSpan("test", "valkey.query")
    def connection = new Connection(new HostAndPort("10.225.132.12", 6379))
    int storeId = FieldBackedContextStores.getContextStoreId(Connection.name, String.name)
    FieldBackedContextStores.getContextStore(storeId).put(connection, "redis.example.com")

    when:
    ValkeyClientDecorator.DECORATE.onConnection(span, connection)

    then:
    span.getTag(Tags.PEER_HOSTNAME) == "10.225.132.12"

    cleanup:
    span.finish()
    TEST_WRITER.clear()
  }
}

class ValkeyDecoratorConfigHostForkedTest extends InstrumentationSpecification {

  @Override
  protected void configurePreAgent() {
    super.configurePreAgent()
    injectSysConfig(PEER_HOSTNAME_FROM_CONFIG_ENABLED, "true")
  }

  def "configured host overrides peer hostname when enabled"() {
    setup:
    def span = TEST_TRACER.startSpan("test", "valkey.query")
    def connection = new Connection(new HostAndPort("10.225.132.12", 6379))
    int storeId = FieldBackedContextStores.getContextStoreId(Connection.name, String.name)
    FieldBackedContextStores.getContextStore(storeId).put(connection, "redis.example.com")

    when:
    ValkeyClientDecorator.DECORATE.onConnection(span, connection)

    then:
    span.getTag(Tags.PEER_HOSTNAME) == "redis.example.com"

    cleanup:
    span.finish()
    TEST_WRITER.clear()
  }
}
