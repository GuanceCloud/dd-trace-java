package datadog.trace.instrumentation.cxf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datadog.trace.junit.utils.config.WithConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.junit.jupiter.api.Test;

class CxfInvokerServerDecoratorTest {

  @Test
  void fallbackInstrumentationIsDisabledByDefault() {
    assertFalse(new CxfInvokerFallbackInstrumentation().defaultEnabled());
  }

  @Test
  @WithConfig(key = "trace.cxf-invoker-fallback.enabled", value = "true")
  void enablingWithoutTargetClassAllowlistFailsClosed() {
    CxfInvokerFallbackInstrumentation instrumentation = new CxfInvokerFallbackInstrumentation();

    assertFalse(instrumentation.isEnabled());
  }

  @Test
  @WithConfig(key = "trace.cxf-invoker-fallback.enabled", value = "true")
  @WithConfig(
      key = "trace.cxf-invoker-fallback.target-classes",
      value = "example.FirstInvoker,example.SecondInvoker")
  void configuredTargetClassAllowlistIsExact() {
    CxfInvokerFallbackInstrumentation instrumentation = new CxfInvokerFallbackInstrumentation();

    assertTrue(instrumentation.isEnabled());
    assertEquals(
        Arrays.asList("example.FirstInvoker", "example.SecondInvoker"),
        instrumentation.configuredMatchingTypes());
  }

  @Test
  void identifiesOnlyInboundHttpRequests() {
    Message request = new MessageImpl();

    assertFalse(CxfInvokerServerDecorator.DECORATE.isInboundHttpRequest(request));

    request.put(Message.HTTP_REQUEST_METHOD, "POST");
    assertTrue(CxfInvokerServerDecorator.DECORATE.isInboundHttpRequest(request));

    request.put(Message.REQUESTOR_ROLE, true);
    assertFalse(CxfInvokerServerDecorator.DECORATE.isInboundHttpRequest(request));
  }

  @Test
  void readsResponseStatusFromNumbersAndStrings() {
    Message response = new MessageImpl();

    response.put(Message.RESPONSE_CODE, 202);
    assertEquals(202, CxfInvokerServerDecorator.DECORATE.status(response));

    response.put(Message.RESPONSE_CODE, "503");
    assertEquals(503, CxfInvokerServerDecorator.DECORATE.status(response));
  }

  @Test
  void visitsEveryProtocolHeaderValue() {
    Message request = new MessageImpl();
    Map<String, Object> headers = new LinkedHashMap<>();
    headers.put("traceparent", Arrays.asList("first", "second"));
    headers.put("x-datadog-trace-id", new String[] {"123"});
    request.put(Message.PROTOCOL_HEADERS, headers);

    List<String> visited = new ArrayList<>();
    CxfMessageHeadersVisitor.INSTANCE.forEachKey(
        request,
        (key, value) -> {
          visited.add(key + '=' + value);
          return true;
        });

    assertEquals(
        Arrays.asList("traceparent=first", "traceparent=second", "x-datadog-trace-id=123"),
        visited);
  }
}
