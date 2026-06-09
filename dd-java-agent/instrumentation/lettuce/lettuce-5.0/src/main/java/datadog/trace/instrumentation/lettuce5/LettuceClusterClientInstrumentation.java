package datadog.trace.instrumentation.lettuce5;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import java.util.Collections;
import java.util.Map;

@AutoService(InstrumenterModule.class)
public final class LettuceClusterClientInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {

  public LettuceClusterClientInstrumentation() {
    super("lettuce", "lettuce-5", "lettuce-5-cluster");
  }

  @Override
  public String instrumentedType() {
    return "io.lettuce.core.cluster.RedisClusterClient";
  }

  @Override
  public Map<String, String> contextStore() {
    return Collections.singletonMap(
        "io.lettuce.core.api.StatefulConnection",
        "datadog.trace.instrumentation.lettuce5.LettuceConnectionInfo");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".LettuceConnectionInfo", packageName + ".ConnectionContextBiConsumer"
    };
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isMethod()
            .and(named("connectStatefulAsync"))
            .and(returns(named("io.lettuce.core.ConnectionFuture"))),
        packageName + ".ClusterConnectionFutureAdvice");
  }
}
