package datadog.trace.instrumentation.axis1;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import datadog.context.ContextScope;
import datadog.trace.agent.test.AbstractInstrumentationTest;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import org.apache.axis.MessageContext;
import org.apache.axis.description.OperationDesc;
import org.junit.jupiter.api.Test;
import test.guancescope.Handler;

class AxisScopeTest extends AbstractInstrumentationTest {
  @Test
  void nestedHandlersRestoreAndDoNotDecorateTheParent() throws Exception {
    MessageContext message = message();
    Handler handler = new Handler();
    AgentSpan parent = startSpan("test", "parent");
    try (ContextScope ignored = activateSpan(parent)) {
      handler.invoke(message);
      assertSame(parent, activeSpan());
      assertNull(parent.getTag("component"));
      assertEquals(1, handler.calls);
    } finally {
      parent.finish();
    }
    writer.waitForTraces(1);
    assertEquals(2, writer.get(0).size());
    assertNull(activeSpan());
  }

  @Test
  void handlerExceptionRestoresParent() throws Exception {
    Handler handler = new Handler();
    handler.failure = new IllegalStateException("business failed");
    AgentSpan parent = startSpan("test", "parent");
    try (ContextScope ignored = activateSpan(parent)) {
      assertSame(
          handler.failure,
          assertThrows(IllegalStateException.class, () -> handler.invoke(message())));
      assertSame(parent, activeSpan());
    } finally {
      parent.finish();
    }
    writer.waitForTraces(1);
    assertEquals(2, writer.get(0).size());
  }

  static MessageContext message() {
    MessageContext message = new MessageContext(null);
    OperationDesc operation = new OperationDesc();
    operation.setName("soapCall");
    message.setOperation(operation);
    return message;
  }
}
