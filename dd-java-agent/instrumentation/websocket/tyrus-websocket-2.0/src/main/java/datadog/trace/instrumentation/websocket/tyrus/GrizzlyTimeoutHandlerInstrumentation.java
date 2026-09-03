package datadog.trace.instrumentation.websocket.tyrus;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.websocket.tyrus.TyrusClientDecorator.DECORATE;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.concurrent.TimeoutException;
import net.bytebuddy.asm.Advice;

public class GrizzlyTimeoutHandlerInstrumentation
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {
  private static final String TIMEOUT_HANDLER =
      "org.glassfish.tyrus.container.grizzly.client.GrizzlyClientSocket$2";
  private static final String TYRUS_CLIENT_ENGINE = "org.glassfish.tyrus.client.TyrusClientEngine";
  private static final String AGENT_SPAN = "datadog.trace.bootstrap.instrumentation.api.AgentSpan";

  @Override
  public String instrumentedType() {
    return TIMEOUT_HANDLER;
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isMethod().and(isPublic()).and(named("handleTimeout")).and(takesArguments(0)),
        getClass().getName() + "$HandleTimeoutAdvice");
  }

  public static class HandleTimeoutAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope onEnter(@Advice.This Object timeoutHandler) {
      Object engine =
          InstrumentationContext.<Object, Object>get(TIMEOUT_HANDLER, TYRUS_CLIENT_ENGINE)
              .remove(timeoutHandler);
      AgentSpan span =
          engine == null
              ? null
              : InstrumentationContext.<Object, AgentSpan>get(TYRUS_CLIENT_ENGINE, AGENT_SPAN)
                  .remove(engine);
      return span == null ? null : activateSpan(span);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Enter AgentScope scope, @Advice.Thrown Throwable throwable) {
      if (scope == null) {
        return;
      }
      AgentSpan span = scope.span();
      try {
        DECORATE.onError(
            span,
            throwable != null
                ? throwable
                : new TimeoutException("WebSocket handshake response was not received"));
        DECORATE.beforeFinish(span);
      } finally {
        scope.close();
        span.finish();
      }
    }
  }
}
