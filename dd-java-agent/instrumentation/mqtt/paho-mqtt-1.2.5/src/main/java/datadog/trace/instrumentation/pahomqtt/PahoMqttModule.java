package datadog.trace.instrumentation.pahomqtt;

import static datadog.trace.bootstrap.instrumentation.java.concurrent.ExcludeFilter.ExcludeType.RUNNABLE;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.ExcludeFilterProvider;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.agent.tooling.muzzle.Reference;
import datadog.trace.bootstrap.instrumentation.java.concurrent.ExcludeFilter;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@AutoService(InstrumenterModule.class)
public class PahoMqttModule extends InstrumenterModule.Tracing implements ExcludeFilterProvider {
  public PahoMqttModule() {
    super("paho-mqtt");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.instrumentation.mqtt.payload.MqttPayload$Decoded",
      "datadog.trace.instrumentation.mqtt.payload.MqttPayload$HeadersSetter",
      "datadog.trace.instrumentation.mqtt.payload.MqttPayload",
      "datadog.trace.instrumentation.pahomqtt.TracingMqttMessage",
      "datadog.trace.instrumentation.pahomqtt.PahoMqttDecorator",
      "datadog.trace.instrumentation.pahomqtt.PublishState"
    };
  }

  @Override
  public List<Instrumenter> typeInstrumentations() {
    return asList(
        new PublishInstrumentation(),
        new SendInstrumentation(),
        new CallbackInstrumentation(),
        new DeliveryTokenInstrumentation());
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("org.eclipse.paho.client.mqttv3.MqttToken", packageName + ".PublishState");
  }

  @Override
  public Map<ExcludeFilter.ExcludeType, ? extends Collection<String>> excludedClasses() {
    return singletonMap(
        RUNNABLE,
        asList(
            "org.eclipse.paho.client.mqttv3.internal.CommsSender",
            "org.eclipse.paho.client.mqttv3.internal.CommsReceiver",
            "org.eclipse.paho.client.mqttv3.internal.CommsCallback"));
  }

  @Override
  public Reference[] additionalMuzzleReferences() {
    return new Reference[] {
      new Reference.Builder("org.eclipse.paho.client.mqttv3.internal.CommsCallback")
          .withMethod(
              new String[0],
              Reference.EXPECTS_NON_STATIC,
              "deliverMessage",
              "Z",
              "Ljava/lang/String;",
              "I",
              "Lorg/eclipse/paho/client/mqttv3/MqttMessage;")
          .build()
    };
  }
}
