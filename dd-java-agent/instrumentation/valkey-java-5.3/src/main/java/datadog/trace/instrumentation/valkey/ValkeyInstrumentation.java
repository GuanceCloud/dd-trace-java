package datadog.trace.instrumentation.valkey;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static io.valkey.ValkeyClientDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import io.valkey.CommandObject;
import io.valkey.Connection;
import io.valkey.DefaultJedisSocketFactory;
import io.valkey.HostAndPort;
import io.valkey.JedisSocketFactory;
import io.valkey.Protocol;
import io.valkey.ValkeyClientDecorator;
import io.valkey.commands.ProtocolCommand;
import java.util.Map;
import net.bytebuddy.asm.Advice;

@AutoService(InstrumenterModule.class)
public final class ValkeyInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {

  public ValkeyInstrumentation() {
    super("valkey");
  }

  @Override
  public String instrumentedType() {
    return "io.valkey.Connection";
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("io.valkey.Connection", String.class.getName());
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.valkey.ValkeyClientDecorator",
    };
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isConstructor(), ValkeyInstrumentation.class.getName() + "$ConnectionConstructorAdvice");

    transformer.applyAdvice(
        isMethod()
            .and(isPublic())
            .and(named("executeCommand"))
            .and(takesArgument(0, named("io.valkey.CommandObject"))),
        ValkeyInstrumentation.class.getName() + "$ValkeyAdvice");
  }

  public static class ConnectionConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void afterConstructor(
        @Advice.This final Connection connection, @Advice.AllArguments final Object[] args) {
      String configuredHost = null;
      if (args != null && args.length > 0) {
        final Object firstArg = args[0];
        if (firstArg instanceof String) {
          configuredHost = (String) firstArg;
        } else if (firstArg instanceof HostAndPort) {
          configuredHost = ((HostAndPort) firstArg).getHost();
        } else if (firstArg instanceof DefaultJedisSocketFactory) {
          final HostAndPort hostAndPort = ((DefaultJedisSocketFactory) firstArg).getHostAndPort();
          configuredHost = hostAndPort != null ? hostAndPort.getHost() : null;
        } else if (firstArg instanceof JedisSocketFactory) {
          configuredHost = null;
        }
      }
      if (configuredHost != null && !configuredHost.isEmpty()) {
        InstrumentationContext.get(Connection.class, String.class).put(connection, configuredHost);
      }
    }
  }

  public static class ValkeyAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope onEnter(
        @Advice.Argument(0) final CommandObject<?> commandObject,
        @Advice.This final Connection thiz) {
      final AgentSpan span = startSpan("valkey-command", ValkeyClientDecorator.OPERATION_NAME);
      DECORATE.afterStart(span);
      DECORATE.onConnection(span, thiz);

      final ProtocolCommand command = commandObject.getArguments().getCommand();

      if (command instanceof Protocol.Command) {
        DECORATE.onStatement(span, ((Protocol.Command) command).name());
      } else {
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
