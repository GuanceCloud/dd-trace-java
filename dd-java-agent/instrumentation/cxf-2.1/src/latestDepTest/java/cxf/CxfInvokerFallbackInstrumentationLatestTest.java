package cxf;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import datadog.trace.agent.test.AbstractInstrumentationTest;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.core.DDSpan;
import datadog.trace.junit.utils.config.WithConfig;
import java.util.List;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.invoker.Invoker;
import org.junit.jupiter.api.Test;

@WithConfig(key = "trace.cxf-invoker-fallback.enabled", value = "true")
@WithConfig(
    key = "trace.cxf-invoker-fallback.target-classes",
    value = "cxf.CxfInvokerFallbackInstrumentationLatestTest$TestInvoker")
class CxfInvokerFallbackInstrumentationLatestTest extends AbstractInstrumentationTest {

  @Test
  void createsServerSpanWhenNoServerContextExists() throws Exception {
    assertNull(activeSpan());
    assertEquals("ok", new TestInvoker().invoke(newHttpExchange(), null));
    assertNull(activeSpan());

    writer.waitForTraces(1);
    List<DDSpan> trace = writer.firstTrace();
    assertEquals(1, trace.size());
    assertEquals("cxf.request", trace.get(0).getOperationName().toString());
    assertEquals(201, trace.get(0).getHttpStatusCode());
  }

  @Test
  void doesNotCreateAnotherSpanWhenOneIsAlreadyActive() throws Exception {
    AgentSpan parent = startSpan("test", "existing.request");
    try (AgentScope ignored = activateSpan(parent)) {
      assertEquals("ok", new TestInvoker().invoke(newHttpExchange(), null));
      assertSame(parent, activeSpan());
    } finally {
      parent.finish();
    }

    writer.waitForTraces(1);
    List<DDSpan> trace = writer.firstTrace();
    assertEquals(1, trace.size());
    assertEquals("existing.request", trace.get(0).getOperationName().toString());
  }

  private static Exchange newHttpExchange() {
    Exchange exchange = new ExchangeImpl();
    Message request = new MessageImpl();
    request.put(Message.HTTP_REQUEST_METHOD, "POST");
    request.put("org.apache.cxf.request.url", "http://localhost/one/trigger");
    exchange.setInMessage(request);
    return exchange;
  }

  public static final class TestInvoker implements Invoker {
    @Override
    public Object invoke(Exchange exchange, Object request) {
      assertNotNull(activeSpan());
      Message response = new MessageImpl();
      response.put(Message.RESPONSE_CODE, 201);
      exchange.setOutMessage(response);
      return "ok";
    }
  }
}
