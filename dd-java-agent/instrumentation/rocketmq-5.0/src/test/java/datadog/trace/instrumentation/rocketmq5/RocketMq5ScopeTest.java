package datadog.trace.instrumentation.rocketmq5;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import datadog.context.ContextScope;
import datadog.trace.agent.test.AbstractInstrumentationTest;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Optional;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.java.message.MessageBuilderImpl;
import org.junit.jupiter.api.Test;

class RocketMq5ScopeTest extends AbstractInstrumentationTest {
  @Test
  void failedBuildRestoresParentAndPreservesValidationException() throws Exception {
    AgentSpan parent = startSpan("test", "parent");
    try (ContextScope ignored = activateSpan(parent)) {
      assertThrows(NullPointerException.class, () -> new MessageBuilderImpl().build());
      assertSame(parent, activeSpan());
    } finally {
      parent.finish();
    }
    writer.waitForTraces(1);
    assertEquals(2, writer.get(0).size());
  }

  @Test
  void listenerPreservesResultExceptionAndParent() throws Exception {
    RuntimeException failure = new IllegalStateException("business failed");
    MessageView message = message();
    AgentSpan parent = startSpan("test", "parent");
    try (ContextScope ignored = activateSpan(parent)) {
      assertEquals(
          ConsumeResult.SUCCESS,
          new MessageListenerWrapper(
                  m -> {
                    assertSame(message, m);
                    return ConsumeResult.SUCCESS;
                  })
              .consume(message));
      assertSame(parent, activeSpan());
      assertSame(
          failure,
          assertThrows(
              IllegalStateException.class,
              () ->
                  new MessageListenerWrapper(
                          m -> {
                            throw failure;
                          })
                      .consume(message)));
      assertSame(parent, activeSpan());
    } finally {
      parent.finish();
    }
    writer.waitForTraces(1);
    assertEquals(3, writer.get(0).size());
  }

  static MessageView message() {
    return (MessageView)
        Proxy.newProxyInstance(
            RocketMq5ScopeTest.class.getClassLoader(),
            new Class<?>[] {MessageView.class},
            (o, m, a) -> {
              switch (m.getName()) {
                case "getProperties":
                  return Collections.emptyMap();
                case "getTopic":
                  return "topic";
                case "getTag":
                case "getMessageGroup":
                case "getDeliveryTimestamp":
                  return Optional.empty();
                case "getKeys":
                  return Collections.emptyList();
                default:
                  return null;
              }
            });
  }
}
