package datadog.trace.instrumentation.websocket.tyrus;

import datadog.context.propagation.CarrierSetter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TyrusHeadersInjectAdapter implements CarrierSetter<Map<String, List<String>>> {
  public static final TyrusHeadersInjectAdapter SETTER = new TyrusHeadersInjectAdapter();

  @Override
  public void set(Map<String, List<String>> carrier, String key, String value) {
    carrier.put(key, Collections.singletonList(value));
  }
}
