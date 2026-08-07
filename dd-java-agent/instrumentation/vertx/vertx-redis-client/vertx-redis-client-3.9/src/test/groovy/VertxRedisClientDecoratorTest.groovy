import static datadog.trace.api.config.TraceInstrumentationConfig.PEER_HOSTNAME_FROM_CONFIG_ENABLED

import datadog.trace.agent.test.InstrumentationSpecification
import datadog.trace.bootstrap.instrumentation.api.Tags
import datadog.trace.instrumentation.vertx_redis_client.RedisOptionsHostParser
import datadog.trace.instrumentation.vertx_redis_client.VertxRedisClientDecorator
import io.vertx.core.net.SocketAddress
import io.vertx.redis.client.RedisOptions

class VertxRedisClientDecoratorTest extends InstrumentationSpecification {

  def "configured host does not override peer hostname by default"() {
    setup:
    def span = TEST_TRACER.startSpan("test", "redis.query")
    def socketAddress = SocketAddress.inetSocketAddress(6379, "10.225.132.12")

    when:
    VertxRedisClientDecorator.DECORATE.onConnection(span, socketAddress, "redis.example.com")

    then:
    span.getTag(Tags.PEER_HOSTNAME) == "10.225.132.12"

    cleanup:
    span.finish()
    TEST_WRITER.clear()
  }

  def "single configured endpoint host can be parsed"() {
    expect:
    RedisOptionsHostParser.configuredHost(new RedisOptions().setConnectionString("redis://redis.example.com:6379"))
    == "redis.example.com"
    RedisOptionsHostParser.configuredHost(new RedisOptions().addEndpoint("redis://redis.example.com:6379"))
    == "redis.example.com"
    RedisOptionsHostParser.configuredHost(new RedisOptions().addEndpoint("redis.example.com:6379"))
    == "redis.example.com"
  }

  def "multiple endpoint hosts are ignored"() {
    expect:
    RedisOptionsHostParser.configuredHost(new RedisOptions()
    .addEndpoint("redis://redis-a.example.com:6379")
    .addEndpoint("redis://redis-b.example.com:6379")) == null
  }
}

class VertxRedisClientDecoratorConfigHostForkedTest extends InstrumentationSpecification {

  @Override
  protected void configurePreAgent() {
    super.configurePreAgent()
    injectSysConfig(PEER_HOSTNAME_FROM_CONFIG_ENABLED, "true")
  }

  def "configured host overrides peer hostname when enabled"() {
    setup:
    def span = TEST_TRACER.startSpan("test", "redis.query")
    def socketAddress = SocketAddress.inetSocketAddress(6379, "10.225.132.12")

    when:
    VertxRedisClientDecorator.DECORATE.onConnection(span, socketAddress, "redis.example.com")

    then:
    span.getTag(Tags.PEER_HOSTNAME) == "redis.example.com"

    cleanup:
    span.finish()
    TEST_WRITER.clear()
  }
}
