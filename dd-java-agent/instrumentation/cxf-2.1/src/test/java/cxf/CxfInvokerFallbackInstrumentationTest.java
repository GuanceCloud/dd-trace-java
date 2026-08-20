package cxf;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import datadog.trace.agent.test.AbstractInstrumentationTest;
import datadog.trace.api.DDTraceId;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.core.DDSpan;
import datadog.trace.junit.utils.config.WithConfig;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.invoker.Invoker;
import org.junit.jupiter.api.Test;

@WithConfig(key = "trace.cxf-invoker-fallback.enabled", value = "true")
@WithConfig(
    key = "trace.cxf-invoker-fallback.target-classes",
    value = "cxf.CxfInvokerFallbackInstrumentationTest$TestInvoker")
class CxfInvokerFallbackInstrumentationTest extends AbstractInstrumentationTest {

  @Test
  void createsServerSpanWhenNoServerContextExists() throws Exception {
    Exchange exchange = newHttpExchange();

    assertNull(activeSpan());
    assertEquals("ok", new TestInvoker().invoke(exchange, null));
    assertNull(activeSpan());

    writer.waitForTraces(1);
    List<DDSpan> trace = writer.firstTrace();
    assertEquals(1, trace.size());
    DDSpan span = trace.get(0);
    assertEquals("cxf.request", span.getOperationName().toString());
    assertEquals("cxf-invoker-fallback", span.getTag("component").toString());
    assertEquals("server", span.getTag("span.kind"));
    assertEquals("POST", span.getTag("http.method"));
    assertEquals(201, span.getHttpStatusCode());
  }

  @Test
  void continuesIncomingDistributedTrace() throws Exception {
    Exchange exchange = newHttpExchange();
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put("x-datadog-trace-id", Collections.singletonList("123"));
    headers.put("x-datadog-parent-id", Collections.singletonList("456"));
    headers.put("x-datadog-sampling-priority", Collections.singletonList("1"));
    exchange.getInMessage().put(Message.PROTOCOL_HEADERS, headers);

    assertEquals("ok", new TestInvoker().invoke(exchange, null));

    writer.waitForTraces(1);
    DDSpan span = writer.firstTrace().get(0);
    assertEquals(DDTraceId.from(123), span.getTraceId());
    assertEquals(456, span.getParentId());
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
    Map<String, List<String>> headers =
        Collections.singletonMap("user-agent", Collections.singletonList("test-client"));
    request.put(Message.PROTOCOL_HEADERS, headers);
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
