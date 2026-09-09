package datadog.trace.instrumentation.redisson30;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.api.Config;
import datadog.trace.bootstrap.InstrumentationContext;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import org.redisson.api.RTransaction;
import org.redisson.client.RedisClient;

@AutoService(InstrumenterModule.class)
public final class ConnectionManagerInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {

  public ConnectionManagerInstrumentation() {
    super("redisson", "redis");
  }

  @Override
  public String instrumentedType() {
    return "org.redisson.connection.MasterSlaveConnectionManager";
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("org.redisson.client.RedisClient", String.class.getName());
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".RedisClientHostParser", packageName + ".ConnectionManagerHost"
    };
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isMethod()
            .and(named("createClient"))
            .and(returns(named("org.redisson.client.RedisClient"))),
        ConnectionManagerInstrumentation.class.getName() + "$CreateClientAdvice");
  }

  public static class CreateClientAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void after(@Advice.This Object manager, @Advice.Return RedisClient client) {
      if (Config.get().isPeerHostnameFromConfigEnabled() && client != null) {
        String host = ConnectionManagerHost.getHost(manager);
        if (host != null && !host.isEmpty()) {
          InstrumentationContext.get(RedisClient.class, String.class).put(client, host);
        }
      }
    }

    public static void muzzleCheck(final RTransaction transaction) {
      transaction.getBuckets();
    }
  }
}
