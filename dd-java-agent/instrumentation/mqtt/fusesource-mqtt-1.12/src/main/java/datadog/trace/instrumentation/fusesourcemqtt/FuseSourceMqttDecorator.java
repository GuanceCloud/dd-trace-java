package datadog.trace.instrumentation.fusesourcemqtt;

import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import datadog.trace.instrumentation.mqtt.MqttClientDecorator;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.QoS;
import org.fusesource.mqtt.codec.PUBLISH;

public class FuseSourceMqttDecorator extends MqttClientDecorator {
  public static final FuseSourceMqttDecorator PRODUCER =
      new FuseSourceMqttDecorator(Tags.SPAN_KIND_PRODUCER);
  public static final FuseSourceMqttDecorator CONSUMER =
      new FuseSourceMqttDecorator(Tags.SPAN_KIND_CONSUMER);

  private FuseSourceMqttDecorator(String kind) {
    super("fusesource-mqtt", kind);
  }

  public AgentSpan start(MQTT mqtt, String topic, QoS qos, boolean retained) {
    return start(
            topic,
            qos.ordinal(),
            retained,
            mqtt == null || mqtt.getClientId() == null ? null : mqtt.getClientId().toString(),
            mqtt == null || mqtt.getHost() == null ? null : mqtt.getHost().toString())
        .setTag("mqtt.message.duplicate", false);
  }

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"fusesource-mqtt"};
  }

  public AgentSpan receive(MQTT mqtt, PUBLISH message) {
    return start(mqtt, message.topicName().toString(), message.qos(), message.retain())
        .setTag("mqtt.message.duplicate", message.dup());
  }
}
