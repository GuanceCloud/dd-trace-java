package datadog.trace.instrumentation.hivemqmqtt;

import com.hivemq.client.mqtt.MqttClientConfig;
import com.hivemq.client.mqtt.MqttClientTransportConfig;
import com.hivemq.client.mqtt.datatypes.MqttClientIdentifier;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import datadog.trace.api.cache.DDCache;
import datadog.trace.api.cache.DDCaches;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import datadog.trace.instrumentation.mqtt.MqttClientDecorator;
import java.net.InetSocketAddress;

public class HiveMqttDecorator extends MqttClientDecorator {
  public static final HiveMqttDecorator PRODUCER = new HiveMqttDecorator(Tags.SPAN_KIND_PRODUCER);
  public static final HiveMqttDecorator CONSUMER = new HiveMqttDecorator(Tags.SPAN_KIND_CONSUMER);
  private static final DDCache<MqttClientTransportConfig, String> SERVER_URIS =
      DDCaches.newFixedSizeCache(32);

  private HiveMqttDecorator(String kind) {
    super("hivemq-mqtt", kind);
  }

  private AgentSpan start(MqttClientConfig config, String topic, int qos, boolean retained) {
    MqttClientIdentifier identifier = config.getClientIdentifier().orElse(null);
    return start(
        topic,
        qos,
        retained,
        identifier == null ? null : identifier.toString(),
        SERVER_URIS.computeIfAbsent(config.getTransportConfig(), HiveMqttDecorator::serverUri));
  }

  private static String serverUri(MqttClientTransportConfig transport) {
    InetSocketAddress address = transport.getServerAddress();
    String host = address.getHostString();
    if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
      host = "[" + host + "]";
    }
    boolean tls = transport.getSslConfig().isPresent();
    String scheme =
        transport.getWebSocketConfig().isPresent() ? (tls ? "wss" : "ws") : (tls ? "ssl" : "tcp");
    return scheme + "://" + host + ":" + address.getPort();
  }

  public AgentSpan start(MqttClientConfig config, Mqtt3Publish publish) {
    return start(
        config, publish.getTopic().toString(), publish.getQos().getCode(), publish.isRetain());
  }

  public AgentSpan start(MqttClientConfig config, Mqtt5Publish publish) {
    return start(
        config, publish.getTopic().toString(), publish.getQos().getCode(), publish.isRetain());
  }

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"hivemq-mqtt"};
  }
}
