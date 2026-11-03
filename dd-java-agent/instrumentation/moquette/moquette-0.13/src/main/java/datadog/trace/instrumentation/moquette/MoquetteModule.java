package datadog.trace.instrumentation.moquette;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.agent.tooling.muzzle.Reference;
import java.util.List;

@AutoService(InstrumenterModule.class)
public class MoquetteModule extends InstrumenterModule.Tracing {
  public MoquetteModule() {
    super("moquette");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.instrumentation.mqtt.payload.MqttPayload$Decoded",
      "datadog.trace.instrumentation.mqtt.payload.MqttPayload$HeadersSetter",
      "datadog.trace.instrumentation.mqtt.payload.MqttPayload",
      "datadog.trace.instrumentation.moquette.MoquetteDecorator",
      "datadog.trace.instrumentation.moquette.MoquettePayload"
    };
  }

  @Override
  public List<Instrumenter> typeInstrumentations() {
    return asList(
        new ConnectionInstrumentation(),
        new PostOfficeInstrumentation(),
        new PublishCallbackInstrumentation(),
        new InterceptMessageInstrumentation());
  }

  @Override
  public Reference[] additionalMuzzleReferences() {
    return new Reference[] {
      new Reference.Builder("io.moquette.broker.MQTTConnection")
          .withMethod(
              new String[0],
              Reference.EXPECTS_NON_STATIC,
              "processPublish",
              "V",
              "Lio/netty/handler/codec/mqtt/MqttPublishMessage;")
          .build()
    };
  }
}
