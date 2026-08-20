package datadog.trace.instrumentation.cxf;

import static datadog.trace.agent.tooling.bytebuddy.matcher.ClassLoaderMatchers.hasClassNamed;
import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromContext;
import static datadog.trace.instrumentation.cxf.CxfInvokerServerDecorator.DECORATE;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.context.Context;
import datadog.context.ContextScope;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.api.InstrumenterConfig;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.AgentTracer;
import datadog.trace.bootstrap.instrumentation.decorator.HttpServerDecorator;
import java.util.Collection;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;

/**
 * Creates an opt-in HTTP server span when CXF invokes a service without an active server context.
 *
 * <p>The regular CXF instrumentation only restores a context previously created by a servlet or
 * another HTTP server integration. This fallback covers CXF deployments with a custom transport
 * where no such context exists.
 */
@AutoService(InstrumenterModule.class)
public final class CxfInvokerFallbackInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForConfiguredTypes, Instrumenter.HasMethodAdvice {

  private final List<String> targetClasses;

  public CxfInvokerFallbackInstrumentation() {
    super("cxf-invoker-fallback");
    targetClasses = InstrumenterConfig.get().getCxfInvokerFallbackTargetClasses();
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public boolean isEnabled() {
    // Fail closed: enabling the integration without an explicit target-class allowlist must never
    // broaden instrumentation to every CXF Invoker implementation in the JVM.
    return super.isEnabled() && !targetClasses.isEmpty();
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassNamed("org.apache.cxf.service.invoker.Invoker");
  }

  @Override
  public Collection<String> configuredMatchingTypes() {
    return targetClasses;
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".ServletHelper",
      packageName + ".CxfMessageHeadersVisitor",
      packageName + ".CxfInvokerServerDecorator",
      packageName + ".CxfInvokerFallbackState",
    };
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isMethod()
            .and(named("invoke"))
            .and(takesArguments(2))
            .and(takesArgument(0, named("org.apache.cxf.message.Exchange"))),
        getClass().getName() + "$FallbackAdvice");
  }

  public static final class FallbackAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static CxfInvokerFallbackState onEnter(@Advice.Argument(0) final Exchange exchange) {
      if (exchange == null || AgentTracer.activeSpan() != null) {
        return null;
      }

      final Message request = exchange.getInMessage();
      if (request == null) {
        return null;
      }

      // Preserve the existing CXF behavior first. This also makes the fallback independent of the
      // order in which the built-in and opt-in advices are applied.
      final Object servletContext =
          ServletHelper.getServletRequestAttribute(
              request.get("HTTP.REQUEST"), HttpServerDecorator.DD_CONTEXT_ATTRIBUTE);
      if (servletContext instanceof Context) {
        return new CxfInvokerFallbackState(((Context) servletContext).attach(), null);
      }

      if (!DECORATE.isInboundHttpRequest(request)) {
        return null;
      }

      ContextScope scope = null;
      AgentSpan span = null;
      try {
        final Context parentContext = DECORATE.extract(request);
        final Context context = DECORATE.startSpan(request, parentContext);
        span = spanFromContext(context);
        scope = context.attach();
        DECORATE.afterStart(span);
        DECORATE.onRequest(span, null, request, parentContext);
        return new CxfInvokerFallbackState(scope, span);
      } catch (final Throwable ignored) {
        try {
          if (scope != null) {
            scope.close();
          }
        } finally {
          if (span != null) {
            span.finish();
          }
        }
        return null;
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) final Exchange exchange,
        @Advice.Enter final CxfInvokerFallbackState state,
        @Advice.Thrown final Throwable throwable) {
      if (state == null) {
        return;
      }

      final AgentSpan span = state.getCreatedSpan();
      try {
        if (span != null) {
          Message response = null;
          if (exchange != null) {
            response = exchange.getOutMessage();
            if (response == null) {
              response = exchange.getOutFaultMessage();
            }
          }
          DECORATE.onResponse(span, response);
          DECORATE.onError(span, throwable);
          DECORATE.beforeFinish(span);
        }
      } finally {
        try {
          state.getScope().close();
        } finally {
          if (span != null) {
            span.finish();
          }
        }
      }
    }
  }
}
