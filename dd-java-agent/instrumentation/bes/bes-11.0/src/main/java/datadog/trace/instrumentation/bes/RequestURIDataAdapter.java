package datadog.trace.instrumentation.bes;

import com.bes.enterprise.webtier.connector.Request;
import datadog.trace.bootstrap.instrumentation.api.URIRawDataAdapter;

final class RequestURIDataAdapter extends URIRawDataAdapter {

  private final Request request;

  RequestURIDataAdapter(final Request request) {
    this.request = request;
  }

  @Override
  public String scheme() {
    return request.getScheme();
  }

  @Override
  public String host() {
    return request.getServerName();
  }

  @Override
  public int port() {
    return request.getServerPort();
  }

  @Override
  protected String innerRawPath() {
    return request.getRequestURI();
  }

  @Override
  public String fragment() {
    return null;
  }

  @Override
  protected String innerRawQuery() {
    return request.getQueryString();
  }

  @Override
  public boolean isValid() {
    return request.getRequestURI() != null;
  }
}
