package datadog.trace.instrumentation.logback;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

@AutoService(Instrumenter.class)
public class PatternLayoutBaseInstrumentation extends Instrumenter.Tracing
    implements Instrumenter.ForTypeHierarchy{

  public static final String CLASS_NAME = "ch.qos.logback.core.pattern.PatternLayoutBase";
  public PatternLayoutBaseInstrumentation() {
    super("logback");
  }

  @Override
  public String hierarchyMarkerType() {
    return CLASS_NAME;
  }

  @Override
  protected boolean defaultEnabled() {
    return true;
  }

  @Override
  public ElementMatcher<TypeDescription> hierarchyMatcher() {
    return named(hierarchyMarkerType());
  }

  @Override
  public void adviceTransformations(AdviceTransformation transformation) {
    transformation.applyAdvice(
        isMethod()
            .and(named("start"))
            .and(takesArguments(0)),
        PatternLayoutBaseInstrumentation.class.getName() + "$StartAdvice");
  }


  public static class StartAdvice{
    @Advice.OnMethodEnter
    public static void onEnter() {
      System.out.println("do StartAdvice,pattern:");
    }
  }
}
