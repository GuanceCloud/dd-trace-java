package datadog.trace.instrumentation.bes;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.agent.tooling.muzzle.Reference.EXPECTS_NON_STATIC;
import static datadog.trace.agent.tooling.muzzle.Reference.EXPECTS_PUBLIC;
import static datadog.context.Context.root;
import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromContext;
import static datadog.trace.bootstrap.instrumentation.decorator.HttpServerDecorator.DD_CONTEXT_ATTRIBUTE;
import static datadog.trace.bootstrap.instrumentation.java.concurrent.ExcludeFilter.ExcludeType.RUNNABLE;
import static datadog.trace.instrumentation.bes.BesDecorator.DD_PARENT_CONTEXT_ATTRIBUTE;
import static datadog.trace.instrumentation.bes.BesDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.bes.enterprise.webtier.connector.CoyoteAdapter;
import com.bes.enterprise.webtier.connector.Request;
import com.bes.enterprise.webtier.connector.Response;
import com.google.auto.service.AutoService;
import datadog.context.Context;
import datadog.context.ContextScope;
import datadog.trace.agent.tooling.ExcludeFilterProvider;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.agent.tooling.annotation.AppliesOn;
import datadog.trace.agent.tooling.muzzle.Reference;
import datadog.trace.api.CorrelationIdentifier;
import datadog.trace.api.gateway.Flow;
import datadog.trace.api.gateway.RequestContext;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.AgentTracer;
import datadog.trace.bootstrap.instrumentation.java.concurrent.ExcludeFilter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import net.bytebuddy.asm.Advice;

@AutoService(InstrumenterModule.class)
public final class BesServerInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice, ExcludeFilterProvider {

  public BesServerInstrumentation() {
    super("bes");
  }

  @Override
  public String instrumentedType() {
    return "com.bes.enterprise.webtier.connector.CoyoteAdapter";
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".ExtractAdapter",
      packageName + ".ExtractAdapter$CraneRequest",
      packageName + ".ExtractAdapter$ConnectorResponse",
      packageName + ".BesDecorator",
      packageName + ".BesDecorator$BesBlockResponseFunction",
      packageName + ".BesBlockingHelper",
      packageName + ".RequestURIDataAdapter",
    };
  }

  private static final Reference GET_OUTPUT_STREAM_REFERENCE =
      new Reference.Builder("com.bes.enterprise.webtier.connector.Response")
          .withMethod(
              new String[0],
              EXPECTS_PUBLIC | EXPECTS_NON_STATIC,
              "getOutputStream",
              "Ljakarta/servlet/ServletOutputStream;")
          .build();

  @Override
  public Reference[] additionalMuzzleReferences() {
    return new Reference[] {GET_OUTPUT_STREAM_REFERENCE};
  }

  @Override
  public void methodAdvice(final MethodTransformer transformer) {
    transformer.applyAdvices(
        named("service")
            .and(takesArgument(0, named("com.bes.enterprise.web.crane.Request")))
            .and(takesArgument(1, named("com.bes.enterprise.web.crane.Response"))),
        BesServerInstrumentation.class.getName()
            + "$ContextTrackingAdvice", // context tracking must be applied first
        BesServerInstrumentation.class.getName() + "$ServiceAdvice");
    transformer.applyAdvice(
        named("postParseRequest")
            .and(takesArgument(0, named("com.bes.enterprise.web.crane.Request")))
            .and(takesArgument(1, named("com.bes.enterprise.webtier.connector.Request")))
            .and(takesArgument(2, named("com.bes.enterprise.web.crane.Response")))
            .and(takesArgument(3, named("com.bes.enterprise.webtier.connector.Response"))),
        BesServerInstrumentation.class.getName() + "$PostParseAdvice");
  }

  @Override
  public Map<ExcludeFilter.ExcludeType, ? extends Collection<String>> excludedClasses() {
    return singletonMap(
        RUNNABLE,
        Arrays.asList(
            "com.bes.enterprise.web.util.threads.WorkThread$WrappingRunnable",
            "com.bes.enterprise.web.util.net.SocketProcessorBase",
            "com.bes.enterprise.web.util.net.NativeEndpoint$Poller",
            "com.bes.enterprise.web.util.net.NioEndpoint$Poller",
            "com.bes.enterprise.web.util.net.NioEndpoint$PollerEvent",
            "com.bes.enterprise.web.util.net.NativeEndpoint$SocketProcessor",
            "com.bes.enterprise.web.util.net.NativeEndpoint$SocketWithOptionsProcessor",
            "com.bes.enterprise.web.util.net.JIoEndpoint$SocketProcessor",
            "com.bes.enterprise.web.util.net.NioEndpoint$SocketProcessor",
            "com.bes.enterprise.web.util.net.Nio2Endpoint$SocketProcessor"));
  }

  @AppliesOn(TargetSystem.CONTEXT_TRACKING)
  public static class ContextTrackingAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void extractParent(
        @Advice.Argument(0) final com.bes.enterprise.web.crane.Request req,
        @Advice.Local("parentScope") ContextScope parentScope) {
      Object existingCtx = req.getAttribute(DD_PARENT_CONTEXT_ATTRIBUTE);
      if (existingCtx instanceof Context) {
        parentScope = ((Context) existingCtx).attach();
      } else {
        final Context parentContext = DECORATE.extract(req);
        req.setAttribute(DD_PARENT_CONTEXT_ATTRIBUTE, parentContext);
        parentScope = parentContext.attach();
      }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void closeScope(@Advice.Local("parentScope") final ContextScope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  public static class ServiceAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onService(
        @Advice.Argument(0) final com.bes.enterprise.web.crane.Request req,
        @Advice.Local("serverScope") ContextScope serverScope) {
      Object existingCtx = req.getAttribute(DD_CONTEXT_ATTRIBUTE);
      if (existingCtx instanceof Context) {
        serverScope = ((Context) existingCtx).attach();
        return;
      }
      final Object parentContextObj = req.getAttribute(DD_PARENT_CONTEXT_ATTRIBUTE);
      final Context parentContext =
          parentContextObj instanceof Context ? (Context) parentContextObj : null;

      final Context context = DECORATE.startSpan(req, parentContext);
      serverScope = context.attach();

      final AgentSpan span = spanFromContext(context);
      DECORATE.afterStart(span);

      req.setAttribute(DD_CONTEXT_ATTRIBUTE, context);
      req.setAttribute(CorrelationIdentifier.getTraceIdKey(), CorrelationIdentifier.getTraceId());
      req.setAttribute(CorrelationIdentifier.getSpanIdKey(), CorrelationIdentifier.getSpanId());
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void closeScope(@Advice.Local("serverScope") final ContextScope serverScope) {
      if (serverScope != null) {
        serverScope.close();
      }
    }

    private void muzzleCheck(
        final CoyoteAdapter adapter, final Request request, final Response response)
        throws Exception {
      adapter.service(null, null);
      request.recycle();
      response.recycle();
    }
  }

  public static class PostParseAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void afterParse(
        @Advice.Argument(1) final Request req,
        @Advice.Argument(3) final Response resp,
        @Advice.Return(readOnly = false) Boolean ret) {
      if (req == null) {
        return;
      }
      Object contextObj = req.getAttribute(DD_CONTEXT_ATTRIBUTE);
      if (contextObj instanceof Context) {
        Context context = (Context) contextObj;
        AgentSpan span = spanFromContext(context);
        if (span != null) {
          req.setAttribute(
              CorrelationIdentifier.getTraceIdKey(), AgentTracer.get().getTraceId(span));
          req.setAttribute(CorrelationIdentifier.getSpanIdKey(), AgentTracer.get().getSpanId(span));
          Object parentContextObj = req.getAttribute(DD_PARENT_CONTEXT_ATTRIBUTE);
          Context parentContext =
              parentContextObj instanceof Context ? (Context) parentContextObj : root();
          DECORATE.onRequest(span, req, req, parentContext);
          Flow.Action.RequestBlockingAction rba = span.getRequestBlockingAction();
          if (rba != null) {
            RequestContext requestContext = span.getRequestContext();
            if (requestContext != null
                && BesBlockingHelper.commitBlockingResponse(
                    requestContext.getTraceSegment(), req, resp, rba)) {
              ret = false;
            }
          }
        }
      }
    }
  }
}
