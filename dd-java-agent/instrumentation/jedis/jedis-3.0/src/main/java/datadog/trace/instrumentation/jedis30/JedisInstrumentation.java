package datadog.trace.instrumentation.jedis30;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.jedis30.JedisClientDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import redis.clients.jedis.Connection;
import redis.clients.jedis.JedisSocketFactory;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.commands.ProtocolCommand;

@AutoService(InstrumenterModule.class)
public final class JedisInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {

  public JedisInstrumentation() {
    super("jedis", "redis");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".JedisClientDecorator",
    };
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
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isConstructor(), JedisInstrumentation.class.getName() + "$ConnectionConstructorAdvice");
    transformer.applyAdvice(
        isMethod()
            .and(named("sendCommand"))
            .and(takesArgument(0, named("redis.clients.jedis.commands.ProtocolCommand")))
            .and(takesArgument(1, byte[][].class)),
        JedisInstrumentation.class.getName() + "$JedisAdvice");
    // FIXME: This instrumentation only incorporates sending the command, not processing the result.
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
        } else if (firstArg instanceof JedisSocketFactory) {
          configuredHost = ((JedisSocketFactory) firstArg).getHost();
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
        @Advice.Argument(0) final ProtocolCommand command,
        @Advice.Argument(1) final byte[][] args,
        @Advice.This final Connection thiz) {
      if (CallDepthThreadLocalMap.incrementCallDepth(Connection.class) > 0) {
        return null;
      }
      final AgentSpan span = startSpan("redis-command", JedisClientDecorator.OPERATION_NAME);
      DECORATE.afterStart(span);
      DECORATE.onConnection(span, thiz);
      StringBuilder sb = new StringBuilder();
      for (byte[] b : args) {
        sb.append(new String(b, java.nio.charset.StandardCharsets.UTF_8)).append(" ");
      }

      DECORATE.setRaw(span, sb.toString());
      DECORATE.setPeerPort(span, thiz.getPort());
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
      if (scope == null) {
        return;
      }
      CallDepthThreadLocalMap.reset(Connection.class);
      DECORATE.onError(scope.span(), throwable);
      DECORATE.beforeFinish(scope.span());
      scope.close();
      scope.span().finish();
    }
  }
}
