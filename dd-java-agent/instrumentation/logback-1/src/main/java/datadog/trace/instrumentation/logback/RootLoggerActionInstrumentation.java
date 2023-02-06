package datadog.trace.instrumentation.logback;

import ch.qos.logback.classic.joran.action.RootLoggerAction;
import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import net.bytebuddy.asm.Advice;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

@AutoService(Instrumenter.class)
public class RootLoggerActionInstrumentation extends Instrumenter.Tracing
    implements Instrumenter.ForSingleType{

  public static final String CLASS_NAME = "ch.qos.logback.classic.joran.action.RootLoggerAction";
  public RootLoggerActionInstrumentation() {
    super("logback");
  }
  @Override
  protected boolean defaultEnabled() {
    return true;
  }
  @Override
  public void adviceTransformations(AdviceTransformation transformation) {
    transformation.applyAdvice(
        isMethod()
            .and(named("begin"))
            .and(takesArguments(3)),
        RootLoggerActionInstrumentation.class.getName() + "$StartAdvice");
  }

  @Override
  public String instrumentedType() {
    return CLASS_NAME;
  }


  public static class StartAdvice{
    @Advice.OnMethodExit
    public static void onExit(@Advice.This RootLoggerAction action) {
      System.out.println("RootLoggerAction");
    }
  }
}
