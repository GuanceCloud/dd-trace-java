package datadog.trace.instrumentation.bes;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import com.bes.enterprise.webtier.connector.Request;
import com.google.auto.service.AutoService;
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
      packageName + ".BesRecycleHelper",
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
      BesRecycleHelper.stopSpan(req);
    }
  }
}
