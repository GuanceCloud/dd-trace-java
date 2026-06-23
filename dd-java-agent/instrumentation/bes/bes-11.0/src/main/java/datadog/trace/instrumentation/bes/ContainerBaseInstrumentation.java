package datadog.trace.instrumentation.bes;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;

import com.bes.enterprise.webtier.core.ContainerBase;
import com.bes.enterprise.webtier.core.DefaultEngine;
import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.api.ProcessTags;
import net.bytebuddy.asm.Advice;

@AutoService(InstrumenterModule.class)
public class ContainerBaseInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {
  public ContainerBaseInstrumentation() {
    super("bes");
  }

  @Override
  public String instrumentedType() {
    return "com.bes.enterprise.webtier.core.ContainerBase";
  }

  @Override
  public void methodAdvice(final MethodTransformer transformer) {
    transformer.applyAdvice(
        isMethod().and(named("setName")), getClass().getName() + "$SetNameAdvice");
  }

  @Override
  public String muzzleDirective() {
    return "bes-processtags";
  }

  public static class SetNameAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void afterSetName(@Advice.This final ContainerBase engine) {
      if (engine instanceof DefaultEngine) {
        String engineName = engine.getName();
        if (engineName != null) {
          ProcessTags.addTag(ProcessTags.SERVER_NAME, engineName);
          ProcessTags.addTag(ProcessTags.SERVER_TYPE, "bes");
        }
      }
    }
  }
}
