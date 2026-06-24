package datadog.trace.instrumentation.bes;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.bootstrap.instrumentation.decorator.HttpServerDecorator.DD_CONTEXT_ATTRIBUTE;
import static datadog.trace.instrumentation.bes.BesDecorator.finishSpan;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import com.bes.enterprise.webtier.connector.CoyoteAdapter;
import com.bes.enterprise.webtier.connector.Request;
import com.bes.enterprise.webtier.connector.Response;
import com.google.auto.service.AutoService;
import datadog.context.Context;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import net.bytebuddy.asm.Advice;

@AutoService(InstrumenterModule.class)
public final class RequestInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {

  public RequestInstrumentation() {
    super("bes");
  }

  @Override
  public String instrumentedType() {
    return "com.bes.enterprise.webtier.connector.Request";
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

  @Override
  public void methodAdvice(final MethodTransformer transformer) {
    transformer.applyAdvice(
        named("recycle").and(takesNoArguments()),
        RequestInstrumentation.class.getName() + "$RecycleAdvice");
  }

  public static class RecycleAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void stopSpan(@Advice.This final Request req) {
      if (req == null) {
        return;
      }
      Response resp = req.getResponse();

      Object contextObj = req.getAttribute(DD_CONTEXT_ATTRIBUTE);

      if (contextObj instanceof Context) {
        clearContext(req);

        final Context context = (Context) contextObj;
        finishSpan(context, resp);
      }
    }

    private static void clearContext(final Request req) {
      com.bes.enterprise.web.crane.Request coyoteRequest = req.getCoyoteRequest();
      if (coyoteRequest != null) {
        coyoteRequest.setAttribute(DD_CONTEXT_ATTRIBUTE, null);
      }
    }

    private void muzzleCheck(final CoyoteAdapter adapter, final Request request) throws Exception {
      adapter.service(null, null);
      request.recycle();
    }
  }
}
