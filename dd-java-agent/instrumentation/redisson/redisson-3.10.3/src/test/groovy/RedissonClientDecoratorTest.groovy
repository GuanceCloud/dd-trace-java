import static datadog.trace.api.config.TraceInstrumentationConfig.PEER_HOSTNAME_FROM_CONFIG_ENABLED

import datadog.trace.agent.test.InstrumentationSpecification
import datadog.trace.bootstrap.instrumentation.api.Tags
import datadog.trace.instrumentation.redisson30.RedissonClientDecorator

import java.net.InetSocketAddress

class RedissonClientDecoratorTest extends InstrumentationSpecification {

  def "configured host does not change peer hostname by default"() {
    setup:
    def span = TEST_TRACER.startSpan("test", "redis.query")
    def remoteAddress = new InetSocketAddress("10.225.132.12", 6379)

    when:
    RedissonClientDecorator.DECORATE.onConnection(span, remoteAddress, "redis.example.com")

    then:
    span.getTag(Tags.PEER_HOSTNAME) == "10.225.132.12"

    cleanup:
    span.finish()
    TEST_WRITER.clear()
  }
}

class RedissonClientDecoratorConfigHostForkedTest extends InstrumentationSpecification {

  @Override
  protected void configurePreAgent() {
    super.configurePreAgent()
    injectSysConfig(PEER_HOSTNAME_FROM_CONFIG_ENABLED, "true")
  }

  def "configured host overrides peer hostname when enabled"() {
    setup:
    def span = TEST_TRACER.startSpan("test", "redis.query")
    def remoteAddress = new InetSocketAddress("10.225.132.12", 6379)

    when:
    RedissonClientDecorator.DECORATE.onConnection(span, remoteAddress, "redis.example.com")

    then:
    span.getTag(Tags.PEER_HOSTNAME) == "redis.example.com"
    span.getTag(Tags.PEER_HOST_IPV4) == "10.225.132.12"

    cleanup:
    span.finish()
    TEST_WRITER.clear()
  }
}
