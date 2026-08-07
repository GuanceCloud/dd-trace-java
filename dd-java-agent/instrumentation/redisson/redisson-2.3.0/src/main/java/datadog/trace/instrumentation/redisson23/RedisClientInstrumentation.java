package datadog.trace.instrumentation.redisson23;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.bootstrap.InstrumentationContext;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import org.redisson.client.RedisClient;

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
        isConstructor(), RedisClientInstrumentation.class.getName() + "$ConstructorAdvice");
  }

  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void after(
        @Advice.This final RedisClient client, @Advice.AllArguments final Object[] args) {
      String host = RedisClientHostParser.hostFrom(args);
      if (host != null) {
        InstrumentationContext.get(RedisClient.class, String.class).put(client, host);
      }
    }
  }
}
