package datadog.trace.instrumentation.cxf;

import static datadog.trace.api.cache.RadixTreeCache.UNSET_STATUS;

import datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import datadog.trace.bootstrap.instrumentation.api.URIDataAdapter;
import datadog.trace.bootstrap.instrumentation.api.URIDataAdapterBase;
import datadog.trace.bootstrap.instrumentation.api.URIDefaultDataAdapter;
import datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;
import datadog.trace.bootstrap.instrumentation.decorator.HttpServerDecorator;
import org.apache.cxf.message.Message;

public final class CxfInvokerServerDecorator
    extends HttpServerDecorator<Message, Object, Message, Message> {
  public static final CxfInvokerServerDecorator DECORATE = new CxfInvokerServerDecorator();

  private static final CharSequence COMPONENT = UTF8BytesString.create("cxf-invoker-fallback");
  private static final CharSequence OPERATION_NAME = UTF8BytesString.create("cxf.request");
  // These keys are used by CXF 2.x, but constants were only added to Message in later releases.
  private static final String REQUEST_URI = "org.apache.cxf.request.uri";
  private static final String REQUEST_URL = "org.apache.cxf.request.url";

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"cxf-invoker-fallback"};
  }

  @Override
  protected CharSequence component() {
    return COMPONENT;
  }

  @Override
  protected AgentPropagation.ContextVisitor<Message> getter() {
    return CxfMessageHeadersVisitor.INSTANCE;
  }

  @Override
  protected AgentPropagation.ContextVisitor<Message> responseGetter() {
    return CxfMessageHeadersVisitor.INSTANCE;
  }

  @Override
  public CharSequence spanName() {
    return OPERATION_NAME;
  }

  @Override
  protected String method(Message request) {
    final Object method = request.get(Message.HTTP_REQUEST_METHOD);
    return method == null ? null : method.toString();
  }

  @Override
  protected URIDataAdapter url(Message request) {
    String url = stringValue(request.get(REQUEST_URL));
    if (url == null) {
      url = stringValue(request.get(REQUEST_URI));
    }
    if (url == null) {
      url = stringValue(request.get(Message.PATH_INFO));
    }
    if (url == null) {
      return null;
    }

    final String query = stringValue(request.get(Message.QUERY_STRING));
    if (query != null && url.indexOf('?') < 0) {
      url = url + '?' + query;
    }
    return URIDataAdapterBase.fromURI(url, URIDefaultDataAdapter::new);
  }

  @Override
  protected String peerHostIP(Object connection) {
    return null;
  }

  @Override
  protected int peerPort(Object connection) {
    return UNSET_PORT;
  }

  @Override
  protected int status(Message response) {
    if (response == null) {
      return UNSET_STATUS;
    }
    final Object status = response.get(Message.RESPONSE_CODE);
    if (status instanceof Number) {
      return ((Number) status).intValue();
    }
    if (status != null) {
      try {
        return Integer.parseInt(status.toString());
      } catch (NumberFormatException ignored) {
        // leave the response status unset
      }
    }
    return UNSET_STATUS;
  }

  public boolean isInboundHttpRequest(Message request) {
    final Object method = request.get(Message.HTTP_REQUEST_METHOD);
    return method != null
        && !method.toString().isEmpty()
        && !Boolean.TRUE.equals(request.get(Message.REQUESTOR_ROLE));
  }

  private static String stringValue(Object value) {
    if (value == null) {
      return null;
    }
    final String string = value.toString();
    return string.isEmpty() ? null : string;
  }
}
