package datadog.trace.instrumentation.fusesourcemqtt;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.agent.tooling.muzzle.Reference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AutoService(InstrumenterModule.class)
public class FuseSourceMqttModule extends InstrumenterModule.Tracing {
  public FuseSourceMqttModule() {
    super("fusesource-mqtt");
  }

  @Override
  public List<Instrumenter> typeInstrumentations() {
    return asList(
        new CallbackConnectionInstrumentation(),
        new FutureConnectionInstrumentation(),
        new PromiseInstrumentation());
  }

  @Override
  public Map<String, String> contextStore() {
    Map<String, String> stores = new HashMap<>();
    stores.put("org.fusesource.mqtt.client.CallbackConnection", "org.fusesource.mqtt.client.MQTT");
    stores.put("org.fusesource.mqtt.client.Promise", packageName + ".PublishState");
    return stores;
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.instrumentation.mqtt.MqttBrokerInfo",
      "datadog.trace.instrumentation.mqtt.MqttClientDecorator",
      packageName + ".FuseSourceMqttDecorator",
      packageName + ".PublishState",
      packageName + ".PublishCallback"
    };
  }

  @Override
  public Reference[] additionalMuzzleReferences() {
    return new Reference[] {
      new Reference.Builder("org.fusesource.mqtt.client.CallbackConnection")
          .withField(
              new String[0],
              Reference.EXPECTS_NON_STATIC,
              "mqtt",
              "Lorg/fusesource/mqtt/client/MQTT;")
          .withMethod(
              new String[0],
              Reference.EXPECTS_NON_STATIC,
              "toReceiver",
              "V",
              "Lorg/fusesource/mqtt/codec/PUBLISH;")
          .withMethod(
              new String[0],
              Reference.EXPECTS_NON_STATIC,
              "handleFatalFailure",
              "V",
              "Ljava/lang/Throwable;")
          .build(),
      new Reference.Builder("org.fusesource.mqtt.client.FutureConnection")
          .withField(
              new String[0],
              Reference.EXPECTS_NON_STATIC,
              "next",
              "Lorg/fusesource/mqtt/client/CallbackConnection;")
          .build()
    };
  }
}
