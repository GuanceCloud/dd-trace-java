package datadog.trace.instrumentation.websocket.tyrus;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.websocket.tyrus.TyrusClientDecorator.DECORATE;
import static datadog.trace.instrumentation.websocket.tyrus.TyrusClientDecorator.WEBSOCKET_HANDSHAKE;
import static datadog.trace.instrumentation.websocket.tyrus.TyrusHeadersInjectAdapter.SETTER;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import org.glassfish.tyrus.spi.ClientEngine;
import org.glassfish.tyrus.spi.UpgradeRequest;
import org.glassfish.tyrus.spi.UpgradeResponse;

public class TyrusClientEngineInstrumentation
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {
  private static final String TYRUS_CLIENT_ENGINE = "org.glassfish.tyrus.client.TyrusClientEngine";
  private static final String GRIZZLY_TIMEOUT_HANDLER =
      "org.glassfish.tyrus.container.grizzly.client.GrizzlyClientSocket$2";
  private static final String AGENT_SPAN = "datadog.trace.bootstrap.instrumentation.api.AgentSpan";

  @Override
  public String instrumentedType() {
    return TYRUS_CLIENT_ENGINE;
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isMethod().and(isPublic()).and(named("createUpgradeRequest")).and(takesArguments(1)),
        getClass().getName() + "$CreateUpgradeRequestAdvice");
    transformer.applyAdvice(
        isMethod()
            .and(isPublic())
            .and(named("processResponse"))
            .and(takesArguments(3))
            .and(takesArgument(0, named("org.glassfish.tyrus.spi.UpgradeResponse"))),
        getClass().getName() + "$ProcessResponseAdvice");
    transformer.applyAdvice(
        isMethod()
            .and(isPublic())
            .and(named("processError"))
            .and(takesArguments(1))
            .and(takesArgument(0, Throwable.class)),
        getClass().getName() + "$ProcessErrorAdvice");
  }

  public static class CreateUpgradeRequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope onEnter(
        @Advice.This Object engine,
        @Advice.Argument(0) Object timeoutHandler,
        @Advice.FieldValue("timeoutHandler") Object previousTimeoutHandler) {
      ContextStore<Object, Object> timeoutStore =
          InstrumentationContext.get(GRIZZLY_TIMEOUT_HANDLER, TYRUS_CLIENT_ENGINE);
      if (previousTimeoutHandler != null && timeoutStore.get(previousTimeoutHandler) == engine) {
        timeoutStore.remove(previousTimeoutHandler);
      }

      ContextStore<Object, AgentSpan> spanStore =
          InstrumentationContext.get(TYRUS_CLIENT_ENGINE, AGENT_SPAN);
      AgentSpan span = spanStore.get(engine);
      if (span == null) {
        span = startSpan("tyrus-websocket", DECORATE.operationName());
        DECORATE.afterStart(span);
        span.setOperationName(WEBSOCKET_HANDSHAKE);
        spanStore.put(engine, span);
      }
      if (timeoutHandler != null) {
        timeoutStore.put(timeoutHandler, engine);
      }
      return activateSpan(span);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.This Object engine,
        @Advice.Argument(0) Object timeoutHandler,
        @Advice.Enter AgentScope scope,
        @Advice.Return UpgradeRequest request,
        @Advice.Thrown Throwable throwable) {
      if (scope == null) {
        return;
      }
      AgentSpan span = scope.span();
      boolean finish = false;
      try {
        if (throwable != null || request == null) {
          ContextStore<Object, AgentSpan> spanStore =
              InstrumentationContext.get(TYRUS_CLIENT_ENGINE, AGENT_SPAN);
          if (spanStore.remove(engine) == span) {
            if (timeoutHandler != null) {
              ContextStore<Object, Object> timeoutStore =
                  InstrumentationContext.get(GRIZZLY_TIMEOUT_HANDLER, TYRUS_CLIENT_ENGINE);
              if (timeoutStore.get(timeoutHandler) == engine) {
                timeoutStore.remove(timeoutHandler);
              }
            }
            DECORATE.onError(
                span,
                throwable != null ? throwable : new IllegalStateException("No upgrade request"));
            DECORATE.beforeFinish(span);
            finish = true;
          }
          return;
        }
        DECORATE.onRequest(span, request);
        Map<String, List<String>> headers = request.getHeaders();
        if (headers != null) {
          DECORATE.injectContext(span, headers, SETTER);
        }
      } finally {
        scope.close();
        if (finish) {
          span.finish();
        }
      }
    }
  }

  public static class ProcessResponseAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope onEnter(@Advice.This Object engine) {
      AgentSpan span =
          InstrumentationContext.<Object, AgentSpan>get(TYRUS_CLIENT_ENGINE, AGENT_SPAN)
              .get(engine);
      return span == null ? null : activateSpan(span);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.This Object engine,
        @Advice.FieldValue("timeoutHandler") Object timeoutHandler,
        @Advice.Enter AgentScope scope,
        @Advice.Argument(0) UpgradeResponse response,
        @Advice.Return ClientEngine.ClientUpgradeInfo upgradeInfo,
        @Advice.Thrown Throwable throwable) {
      if (scope == null) {
        return;
      }

      AgentSpan span = scope.span();
      boolean removed = false;
      try {
        boolean terminal =
            throwable != null
                || upgradeInfo == null
                || upgradeInfo.getUpgradeStatus()
                    != ClientEngine.ClientUpgradeStatus.ANOTHER_UPGRADE_REQUEST_REQUIRED;
        if (terminal) {
          ContextStore<Object, AgentSpan> spanStore =
              InstrumentationContext.get(TYRUS_CLIENT_ENGINE, AGENT_SPAN);
          removed = spanStore.remove(engine) == span;
          if (removed) {
            if (timeoutHandler != null) {
              ContextStore<Object, Object> timeoutStore =
                  InstrumentationContext.get(GRIZZLY_TIMEOUT_HANDLER, TYRUS_CLIENT_ENGINE);
              if (timeoutStore.get(timeoutHandler) == engine) {
                timeoutStore.remove(timeoutHandler);
              }
            }
            DECORATE.onResponse(span, response);
            DECORATE.onError(span, throwable);
            if (throwable == null && response != null && response.getStatus() == 101) {
              DECORATE.onHandshakeSuccess(span);
            }
            DECORATE.beforeFinish(span);
          }
        }
      } finally {
        scope.close();
        if (removed) {
          span.finish();
        }
      }
    }
  }

  public static class ProcessErrorAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope onEnter(
        @Advice.This Object engine, @Advice.FieldValue("timeoutHandler") Object timeoutHandler) {
      AgentSpan span =
          InstrumentationContext.<Object, AgentSpan>get(TYRUS_CLIENT_ENGINE, AGENT_SPAN)
              .remove(engine);
      if (span != null) {
        if (timeoutHandler != null) {
          ContextStore<Object, Object> timeoutStore =
              InstrumentationContext.get(GRIZZLY_TIMEOUT_HANDLER, TYRUS_CLIENT_ENGINE);
          if (timeoutStore.get(timeoutHandler) == engine) {
            timeoutStore.remove(timeoutHandler);
          }
        }
        return activateSpan(span);
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Enter AgentScope scope,
        @Advice.Argument(0) Throwable error,
        @Advice.Thrown Throwable throwable) {
      if (scope == null) {
        return;
      }
      AgentSpan span = scope.span();
      try {
        DECORATE.onError(span, throwable != null ? throwable : error);
        DECORATE.beforeFinish(span);
      } finally {
        scope.close();
        span.finish();
      }
    }
  }
}
