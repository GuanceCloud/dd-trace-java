package datadog.trace.instrumentation.websocket.tyrus;

import static datadog.trace.agent.tooling.bytebuddy.matcher.ClassLoaderMatchers.hasClassNamed;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.api.InstrumenterConfig;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumenterModule.class)
public class TyrusWebsocketModule extends InstrumenterModule.Tracing {

  public TyrusWebsocketModule() {
    super("tyrus-websocket", "websocket");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassNamed("jakarta.websocket.Endpoint")
        .and(hasClassNamed("org.glassfish.tyrus.container.grizzly.client.GrizzlyClientContainer"));
  }

  @Override
  public Map<String, String> contextStore() {
    Map<String, String> contextStores = new HashMap<>();
    contextStores.put("org.glassfish.tyrus.client.TyrusClientEngine", AgentSpan.class.getName());
    contextStores.put(
        "org.glassfish.tyrus.container.grizzly.client.GrizzlyClientSocket$2",
        "org.glassfish.tyrus.client.TyrusClientEngine");
    return contextStores;
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".TyrusClientDecorator", packageName + ".TyrusHeadersInjectAdapter"
    };
  }

  @Override
  protected boolean defaultEnabled() {
    return InstrumenterConfig.get().isWebsocketTracingEnabled();
  }

  @Override
  public String muzzleDirective() {
    return "tyrus-websocket";
  }

  @Override
  public List<Instrumenter> typeInstrumentations() {
    return Arrays.asList(
        new TyrusClientEngineInstrumentation(), new GrizzlyTimeoutHandlerInstrumentation());
  }
}
