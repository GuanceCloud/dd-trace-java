package datadog.trace.instrumentation.redisson30;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.bootstrap.InstrumentationContext;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisClientConfig;

@AutoService(InstrumenterModule.class)
public final class RedisClientInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {

  public RedisClientInstrumentation() {
    super("redisson", "redis");
  }

  @Override
  public String instrumentedType() {
    return "org.redisson.client.RedisClient";
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("org.redisson.client.RedisClient", String.class.getName());
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {packageName + ".RedisClientHostParser"};
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isMethod()
            .and(isStatic())
            .and(named("create"))
            .and(takesArgument(0, named("org.redisson.client.RedisClientConfig")))
            .and(returns(named("org.redisson.client.RedisClient"))),
        RedisClientInstrumentation.class.getName() + "$CreateAdvice");
  }

  public static class CreateAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void after(
        @Advice.Argument(0) final RedisClientConfig config,
        @Advice.Return final RedisClient client) {
      if (client == null || config == null) {
        return;
      }
      String host = RedisClientHostParser.hostFrom(config.getAddress());
      if (host != null) {
        InstrumentationContext.get(RedisClient.class, String.class).put(client, host);
      }
    }
  }
}
