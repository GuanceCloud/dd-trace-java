package datadog.trace.instrumentation.jedis40;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static redis.clients.jedis.JedisClientDecorator.DECORATE;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import redis.clients.jedis.CommandObject;
import redis.clients.jedis.Connection;
import redis.clients.jedis.DefaultJedisSocketFactory;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientDecorator;
import redis.clients.jedis.JedisSocketFactory;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.args.Rawable;
import redis.clients.jedis.commands.ProtocolCommand;

@AutoService(InstrumenterModule.class)
public final class JedisInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {

  public JedisInstrumentation() {
    super("jedis", "redis");
  }

  @Override
  public String instrumentedType() {
    return "redis.clients.jedis.Connection";
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("redis.clients.jedis.Connection", String.class.getName());
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "redis.clients.jedis.JedisClientDecorator",
    };
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isConstructor(), JedisInstrumentation.class.getName() + "$ConnectionConstructorAdvice");
    transformer.applyAdvice(
        isMethod()
            .and(isPublic())
            .and(named("executeCommand"))
            .and(takesArgument(0, named("redis.clients.jedis.CommandObject"))),
        JedisInstrumentation.class.getName() + "$JedisAdvice");
  }

  public static class ConnectionConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void after(
        @Advice.This final Connection connection, @Advice.AllArguments final Object[] args) {
      String configuredHost = null;
      if (args.length > 0) {
        Object firstArg = args[0];
        if (firstArg instanceof String) {
          configuredHost = (String) firstArg;
        } else if (firstArg instanceof HostAndPort) {
          configuredHost = ((HostAndPort) firstArg).getHost();
        } else if (firstArg instanceof DefaultJedisSocketFactory) {
          configuredHost = ((DefaultJedisSocketFactory) firstArg).getHostAndPort().getHost();
        } else if (firstArg instanceof JedisSocketFactory) {
          configuredHost = null;
        }
      }
      if (configuredHost != null && !configuredHost.isEmpty()) {
        InstrumentationContext.get(Connection.class, String.class).put(connection, configuredHost);
      }
    }
  }

  public static class JedisAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope onEnter(
        @Advice.Argument(0) final CommandObject<?> commandObject,
        @Advice.This final Connection thiz) {
      final AgentSpan span = startSpan("redis-command", JedisClientDecorator.OPERATION_NAME);
      DECORATE.afterStart(span);
      DECORATE.onConnection(span, thiz);

      final ProtocolCommand command = commandObject.getArguments().getCommand();
      StringBuilder sb = new StringBuilder();

      for (Rawable raw : commandObject.getArguments()) {
        sb.append(new String(raw.getRaw(), java.nio.charset.StandardCharsets.UTF_8)).append(",");
      }

      DECORATE.setRaw(span, sb.toString());
      if (command instanceof Protocol.Command) {
        DECORATE.onStatement(span, ((Protocol.Command) command).name());
      } else {
        // Protocol.Command is the only implementation in the Jedis lib as of 3.1 but this will save
        // us if that changes
        DECORATE.onStatement(span, new String(command.getRaw()));
      }
      return activateSpan(span);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final AgentScope scope, @Advice.Thrown final Throwable throwable) {
      DECORATE.onError(scope.span(), throwable);
      DECORATE.beforeFinish(scope.span());
      scope.close();
      scope.span().finish();
    }
  }
}
