package datadog.trace.instrumentation.pulsar.telemetry;

import datadog.trace.instrumentation.pulsar.UrlParser.UrlData;

public class BasePulsarRequest {
  private final String destination;
  private final UrlData urlData;

  protected BasePulsarRequest(String destination, UrlData urlData) {
    this.destination = destination;
    this.urlData = urlData;
  }

  public String getDestination() {
    return destination;
  }

  public UrlData getUrlData() {
    return urlData;
  }
}
