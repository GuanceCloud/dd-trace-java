import datadog.trace.agent.test.InstrumentationSpecification
import datadog.trace.agent.test.utils.PortUtils
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.testcontainers.containers.RabbitMQContainer
import rabbitbatch.BatchRabbitMQApplication
import rabbitbatch.BatchReceiver
import spock.lang.Shared

import java.util.concurrent.TimeUnit

import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

class SpringAmqpBatchTest extends InstrumentationSpecification {

  @Shared
  RabbitMQContainer rabbit

  @Override
  def setupSpec() {
    rabbit = new RabbitMQContainer("rabbitmq:3.9.20-alpine")
    rabbit.start()
    def hostName = rabbit.getHost()
    def port = rabbit.getMappedPort(BatchRabbitMQApplication.port)
    BatchRabbitMQApplication.hostName = hostName
    BatchRabbitMQApplication.port = port
    PortUtils.waitForPortToOpen(hostName, port, 5, TimeUnit.SECONDS)
  }

  @Override
  def cleanupSpec() {
    if (null != rabbit) {
      rabbit.close()
    }
  }

  def "test batch listener is traced"() {
    setup:
    def application = BatchRabbitMQApplication.run()
    def template = application.getBean(RabbitTemplate)
    def batchReceiver = application.getBean(BatchReceiver)
    TEST_WRITER.waitForTraces(7)
    TEST_WRITER.clear()

    when: "3 messages from separate traces are sent in a batch"
    (1..3).each {
      int messageNumber = it
      runUnderTrace("parent-$messageNumber") {
        template.convertAndSend(BatchRabbitMQApplication.topicExchangeName, "batch.route", "message$messageNumber")
      }
    }

    then: "the batch listener receives all messages in one callback"
    awaitCondition({ batchReceiver.received.get() == 3 }, 5, TimeUnit.SECONDS)
    batchReceiver.batches.get() == 1

    and: "one consume span provides the batch log correlation context"
    awaitCondition({
      consumeSpans().size() == 1 && parentSpans().size() == 3 && deliverySpans().size() == 3
    }, 5, TimeUnit.SECONDS)
    def consumeSpan = consumeSpans().first()
    def parents = parentSpans()
    consumeSpan.resourceName.toString() == "amqp.consume batch-queue"
    parents*.traceId.toSet().size() == 3
    consumeSpan.traceId == parents.find { it.operationName.toString() == "parent-1" }.traceId
    batchReceiver.traceId.get() == consumeSpan.traceId.toString()
    batchReceiver.spanId.get() == Long.toUnsignedString(consumeSpan.spanId)
    batchReceiver.traceId.get() != "0"
    batchReceiver.spanId.get() != "0"

    cleanup:
    application.close()
  }

  def consumeSpans() {
    TEST_WRITER.flatten().findAll {
      it.operationName.toString() == "amqp.consume" && it.resourceName.toString() == "amqp.consume batch-queue"
    }
  }

  def parentSpans() {
    TEST_WRITER.flatten().findAll {
      it.operationName.toString().startsWith("parent-")
    }
  }

  def deliverySpans() {
    TEST_WRITER.flatten().findAll {
      it.operationName.toString() == "amqp.command" && it.resourceName.toString() == "basic.deliver batch-queue"
    }
  }

  static boolean awaitCondition(Closure<Boolean> condition, long timeout, TimeUnit unit) {
    long deadline = System.nanoTime() + unit.toNanos(timeout)
    while (System.nanoTime() < deadline) {
      if (condition.call()) {
        return true
      }
      Thread.sleep(100)
    }
    return condition.call()
  }
}
