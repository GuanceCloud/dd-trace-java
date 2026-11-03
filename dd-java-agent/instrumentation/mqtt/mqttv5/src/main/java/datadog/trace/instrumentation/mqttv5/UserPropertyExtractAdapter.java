package datadog.trace.instrumentation.mqttv5;

import datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import java.util.List;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserPropertyExtractAdapter
    implements AgentPropagation.ContextVisitor<List<UserProperty>> {
  private static final Logger log = LoggerFactory.getLogger(UserPropertyExtractAdapter.class);
  public static final UserPropertyExtractAdapter GETTER = new UserPropertyExtractAdapter();

  @Override
  public void forEachKey(List<UserProperty> carriers, AgentPropagation.KeyClassifier classifier) {
    if (carriers == null) {
      return;
    }
    for (UserProperty carrier : carriers) {
      if (carrier != null && carrier.getValue() != null) {
        if (!classifier.accept(carrier.getKey(), carrier.getValue())) {
          return;
        }
      }
    }
  }
}
