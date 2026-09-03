package datadog.trace.instrumentation.websocket.tyrus;

import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.InternalSpanTypes;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;
import datadog.trace.bootstrap.instrumentation.decorator.HttpClientDecorator;
import java.net.URI;
import org.glassfish.tyrus.spi.UpgradeRequest;
import org.glassfish.tyrus.spi.UpgradeResponse;

public class TyrusClientDecorator extends HttpClientDecorator<UpgradeRequest, UpgradeResponse> {
  public static final CharSequence TYRUS_WEBSOCKET_CLIENT =
      UTF8BytesString.create("tyrus-websocket-client");
  public static final CharSequence WEBSOCKET_HANDSHAKE =
      UTF8BytesString.create("websocket.handshake");
  public static final CharSequence WEBSOCKET_OPEN = UTF8BytesString.create("websocket.open");

  private static final String[] INSTRUMENTATION_NAMES = {"websocket"};
  public static final TyrusClientDecorator DECORATE = new TyrusClientDecorator();

  @Override
  protected String[] instrumentationNames() {
    return INSTRUMENTATION_NAMES;
  }

  @Override
  protected CharSequence component() {
    return TYRUS_WEBSOCKET_CLIENT;
  }

  @Override
  protected CharSequence spanType() {
    return InternalSpanTypes.WEBSOCKET;
  }

  public void onHandshakeSuccess(AgentSpan span) {
    span.setOperationName(WEBSOCKET_OPEN);
    span.setTag("websocket.handshake.success", true);
    span.setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT);
  }

  @Override
  protected String method(UpgradeRequest request) {
    return "GET";
  }

  @Override
  protected URI url(UpgradeRequest request) {
    return request.getRequestURI();
  }

  @Override
  protected int status(UpgradeResponse response) {
    return response.getStatus();
  }

  @Override
  protected String getRequestHeader(UpgradeRequest request, String headerName) {
    return request.getHeader(headerName);
  }

  @Override
  protected String getResponseHeader(UpgradeResponse response, String headerName) {
    return response.getFirstHeaderValue(headerName);
  }
}
