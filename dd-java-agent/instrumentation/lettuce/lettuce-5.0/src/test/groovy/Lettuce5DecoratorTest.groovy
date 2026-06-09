import static datadog.trace.api.config.TraceInstrumentationConfig.PEER_HOSTNAME_FROM_CONFIG_ENABLED

import datadog.trace.agent.test.InstrumentationSpecification
import datadog.trace.bootstrap.instrumentation.api.Tags
import datadog.trace.instrumentation.lettuce5.LettuceClientDecorator
import datadog.trace.instrumentation.lettuce5.LettuceConnectionInfo
import io.lettuce.core.RedisURI

import java.net.InetAddress
import java.net.InetSocketAddress

class Lettuce5DecoratorTest extends InstrumentationSpecification {

  def "connection info uses redis uri host without adding peer ip by default"() {
    setup:
    def span = TEST_TRACER.startSpan("test", "redis.query")
    def redisURI = RedisURI.create("redis://redis.example.com:6379/0")
    def remoteAddress = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 6379)

    when:
    LettuceClientDecorator.DECORATE.onConnection(
      span, new LettuceConnectionInfo(redisURI, remoteAddress, false))

    then:
    span.getTag(Tags.PEER_HOSTNAME) == "redis.example.com"
    span.getTag(Tags.PEER_HOST_IPV4) == null

    cleanup:
    span.finish()
    TEST_WRITER.clear()
  }

  def "cluster fallback does not change peer tags by default"() {
    setup:
    def span = TEST_TRACER.startSpan("test", "redis.query")
    def redisURI = RedisURI.create("redis://redis.example.com:6379/0")
    def remoteAddress = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 6379)

    when:
    LettuceClientDecorator.DECORATE.onConnection(
      span, new LettuceConnectionInfo(redisURI, remoteAddress, true))

    then:
    span.getTag(Tags.PEER_HOSTNAME) == null
    span.getTag(Tags.PEER_HOST_IPV4) == null

    cleanup:
    span.finish()
    TEST_WRITER.clear()
  }
}

class Lettuce5DecoratorConfigHostForkedTest extends InstrumentationSpecification {

  @Override
  protected void configurePreAgent() {
    super.configurePreAgent()
    injectSysConfig(PEER_HOSTNAME_FROM_CONFIG_ENABLED, "true")
  }

  def "cluster fallback uses redis uri host when enabled"() {
    setup:
    def span = TEST_TRACER.startSpan("test", "redis.query")
    def redisURI = RedisURI.create("redis://redis.example.com:6379/0")
    def remoteAddress = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 6379)

    when:
    LettuceClientDecorator.DECORATE.onConnection(
      span, new LettuceConnectionInfo(redisURI, remoteAddress, true))

    then:
    span.getTag(Tags.PEER_HOSTNAME) == "redis.example.com"
    span.getTag(Tags.PEER_HOST_IPV4) == "127.0.0.1"

    cleanup:
    span.finish()
    TEST_WRITER.clear()
  }
}
