package datadog.trace.instrumentation.pahomqtt;

import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import datadog.trace.instrumentation.mqtt.MqttClientDecorator;
import datadog.trace.instrumentation.mqtt.payload.MqttPayload;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class PahoMqttDecorator extends MqttClientDecorator {
  public static final PahoMqttDecorator PRODUCER = new PahoMqttDecorator(Tags.SPAN_KIND_PRODUCER);
  public static final PahoMqttDecorator CONSUMER = new PahoMqttDecorator(Tags.SPAN_KIND_CONSUMER);

  private PahoMqttDecorator(String kind) {
    super("paho-mqtt", kind);
  }

  public AgentSpan start(String topic, MqttMessage message, String clientId, String serverUri) {
    return start(topic, message, clientId, serverUri, null);
  }

  public AgentSpan start(
      String topic,
      MqttMessage message,
      String clientId,
      String serverUri,
      MqttPayload.Decoded decoded) {
    return start(
            topic,
            message.getQos(),
            message.isRetained(),
            clientId,
            serverUri,
            decoded == null ? null : decoded.parent())
        .setTag("mqtt.message.duplicate", message.isDuplicate());
  }

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"paho-mqtt"};
  }
}
