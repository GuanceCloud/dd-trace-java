package datadog.trace.instrumentation.mqtt;

import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import java.net.URI;
import java.net.URISyntaxException;

/** Broker identity without credentials, paths, or query parameters. */
public final class MqttBrokerInfo {
  private static final MqttBrokerInfo UNKNOWN = new MqttBrokerInfo(null, null, null, 0);
  private final String uri;
  private final String host;
  private final String peerService;
  private final int port;

  private MqttBrokerInfo(String uri, String host, String peerService, int port) {
    this.uri = uri;
    this.host = host;
    this.peerService = peerService;
    this.port = port;
  }

  public static MqttBrokerInfo parse(String serverUri) {
    try {
      URI parsed = new URI(serverUri);
      String host = parsed.getHost();
      if (host == null || parsed.getScheme() == null) {
        return UNKNOWN;
      }
      int port = parsed.getPort();
      if (port < 0) {
        String scheme = parsed.getScheme();
        port =
            "ssl".equals(scheme) || "tls".equals(scheme)
                ? 8883
                : "wss".equals(scheme) ? 443 : "ws".equals(scheme) ? 80 : 1883;
      }
      String authority = host + ":" + port;
      return new MqttBrokerInfo(parsed.getScheme() + "://" + authority, host, authority, port);
    } catch (URISyntaxException ignored) {
      return UNKNOWN;
    }
  }

  public void decorate(AgentSpan span) {
    if (host != null) {
      span.setTag("mqtt.serverURI", uri);
      span.setTag(Tags.PEER_HOSTNAME, host);
      span.setTag(Tags.PEER_PORT, port);
      span.setTag(Tags.PEER_SERVICE, peerService);
    }
  }
}
