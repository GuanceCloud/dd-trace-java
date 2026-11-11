package datadog.trace.instrumentation.hivemqmqtt;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.agent.tooling.muzzle.Reference;
import java.util.List;
import java.util.Map;

@AutoService(InstrumenterModule.class)
public class HiveMqttModule extends InstrumenterModule.Tracing {
  public HiveMqttModule() {
    super("hivemq-mqtt");
  }

  @Override
  public List<Instrumenter> typeInstrumentations() {
    return asList(
        new PublishInstrumentation(),
        new AsyncClientInstrumentation(),
        new RxClientInstrumentation(),
        new BlockingClientInstrumentation(),
        new BlockingReceiveInstrumentation());
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap(
        "com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient$Mqtt3Publishes",
        "com.hivemq.client.mqtt.MqttClientConfig");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.instrumentation.mqtt.MqttBrokerInfo",
      "datadog.trace.instrumentation.mqtt.MqttClientDecorator",
      packageName + ".HiveMqttDecorator",
      packageName + ".PublishState",
      packageName + ".PublishObserver",
      packageName + ".PublishSubscriber",
      packageName + ".ConsumeCallback",
      packageName + ".ConsumeSubscriber",
      packageName + ".TracingPublishes"
    };
  }

  @Override
  public Reference[] additionalMuzzleReferences() {
    return new Reference[] {
      new Reference.Builder(
              "com.hivemq.client.internal.mqtt.handler.publish.outgoing.MqttAckSingle")
          .withField(
              new String[0],
              Reference.EXPECTS_NON_STATIC,
              "clientConfig",
              "Lcom/hivemq/client/internal/mqtt/MqttClientConfig;")
          .withField(
              new String[0],
              Reference.EXPECTS_NON_STATIC,
              "publish",
              "Lcom/hivemq/client/internal/mqtt/message/publish/MqttPublish;")
          .withMethod(
              new String[0],
              Reference.EXPECTS_NON_STATIC,
              "subscribeActual",
              "V",
              "Lio/reactivex/SingleObserver;")
          .build(),
      new Reference.Builder(
              "com.hivemq.client.internal.mqtt.handler.publish.outgoing.MqttAckSingleFlowable")
          .withField(
              new String[0],
              Reference.EXPECTS_NON_STATIC,
              "clientConfig",
              "Lcom/hivemq/client/internal/mqtt/MqttClientConfig;")
          .withField(
              new String[0],
              Reference.EXPECTS_NON_STATIC,
              "publish",
              "Lcom/hivemq/client/internal/mqtt/message/publish/MqttPublish;")
          .withMethod(
              new String[0],
              Reference.EXPECTS_NON_STATIC,
              "subscribeActual",
              "V",
              "Lorg/reactivestreams/Subscriber;")
          .build(),
      new Reference.Builder("com.hivemq.client.internal.mqtt.mqtt3.Mqtt3AsyncClientView").build(),
      new Reference.Builder("com.hivemq.client.internal.mqtt.mqtt3.Mqtt3RxClientView").build(),
      new Reference.Builder(
              "com.hivemq.client.internal.mqtt.mqtt3.Mqtt3BlockingClientView$Mqtt3PublishesView")
          .withMethod(
              new String[0],
              Reference.EXPECTS_NON_STATIC,
              "receive",
              "Lcom/hivemq/client/mqtt/mqtt3/message/publish/Mqtt3Publish;")
          .build()
    };
  }
}
