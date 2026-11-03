package datadog.trace.instrumentation.pulsar;

import static datadog.trace.bootstrap.instrumentation.api.AgentPropagation.extractContextAndGetSpanContext;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.pulsar.MessageTextMapGetter.GETTER;
import static datadog.trace.instrumentation.pulsar.PulsarRequest.create;

import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.AgentSpanContext;
import datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;
import datadog.trace.bootstrap.instrumentation.decorator.BaseDecorator;
import java.util.concurrent.CompletableFuture;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsumerDecorator extends BaseDecorator {
  private static final Logger log = LoggerFactory.getLogger(ConsumerDecorator.class);

  public static final CharSequence PULSAR_NAME = UTF8BytesString.create("queue");
  private static final String TOPIC = "topic";
  private static final String LOCAL_SERVICE_NAME = "pulsar";
  private static final String MESSAGING_ID = "messaging.id";
  private static final String MESSAGING_SYSTEM = "messaging.system";

  ConsumerDecorator() {}

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"pulsar", "pulsar-client"};
  }

  @Override
  protected CharSequence spanType() {
    return PULSAR_NAME;
  }

  @Override
  protected CharSequence component() {
    return null;
  }

  public static void startAndEnd(PulsarRequest pr, Throwable throwable, String brokerUrl) {
    if (log.isDebugEnabled()) {
      log.debug("into startAndEnd");
    }
    AgentSpanContext parentContext = extractContextAndGetSpanContext(pr, GETTER);
    String topic = pr.getMessage().getTopicName();
    UTF8BytesString spanName = UTF8BytesString.create(topic + " receive");
    final AgentSpan span = startSpan("pulsar", spanName, parentContext);
    try {
      span.setResourceName(spanName);
      span.setTag(TOPIC, pr.getMessage().getTopicName());
      span.setTag("destination", pr.getDestination());
      span.setTag("broker_url", brokerUrl);
      span.setTag(MESSAGING_SYSTEM, LOCAL_SERVICE_NAME);
      span.setSpanType(PULSAR_NAME);
      span.setTag(MESSAGING_ID, pr.getMessage().getMessageId());
      span.setServiceName(LOCAL_SERVICE_NAME);
      if (throwable != null) {
        span.setError(true);
        span.setErrorMessage(throwable.getMessage());
      }

    } finally {
      span.finish();
    }
    if (log.isDebugEnabled()) {
      log.debug("out startAndEnd");
    }
  }

  public static CompletableFuture<Message<?>> wrap(
      CompletableFuture<Message<?>> future, String brokerUrl) {
    if (future != null) {
      future.whenComplete(
          (message, throwable) -> {
            if (throwable == null && message != null) {
              try {
                startAndEnd(create(message), null, brokerUrl);
              } catch (Throwable error) {
                log.debug("Unable to trace received Pulsar message", error);
              }
            }
          });
    }
    // Observe the SDK future without changing completion or cancellation semantics.
    return future;
  }

  public static CompletableFuture<Messages<?>> wrapBatch(
      CompletableFuture<Messages<?>> future, String brokerUrl) {
    if (future != null) {
      future.whenComplete(
          (messages, throwable) -> {
            if (throwable == null && messages != null) {
              try {
                for (Message<?> message : messages) {
                  if (message != null) {
                    startAndEnd(create(message), null, brokerUrl);
                  }
                }
              } catch (Throwable error) {
                log.debug("Unable to trace received Pulsar batch", error);
              }
            }
          });
    }
    return future;
  }
}
