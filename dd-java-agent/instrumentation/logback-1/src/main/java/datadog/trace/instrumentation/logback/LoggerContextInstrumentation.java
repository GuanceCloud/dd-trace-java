package datadog.trace.instrumentation.logback;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static datadog.trace.agent.tooling.bytebuddy.matcher.HierarchyMatchers.implementsInterface;
import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

@AutoService(Instrumenter.class)
public class LoggerContextInstrumentation extends Instrumenter.Tracing
    implements Instrumenter.ForTypeHierarchy{
  public LoggerContextInstrumentation() {
    super("logback");
  }

  public static final String CLASSPATH= "ch.qos.logback.classic.LoggerContext";


  @Override
  public String hierarchyMarkerType() {
    return CLASSPATH;
  }

  @Override
  public ElementMatcher<TypeDescription> hierarchyMatcher() {
    return implementsInterface(named(hierarchyMarkerType()));
  }
  @Override
  public void adviceTransformations(AdviceTransformation transformation) {
    transformation.applyAdvice(
        isConstructor()
            .and(named("LoggerContext"))
            .and(takesArguments(0)),
        LoggerContextInstrumentation.class.getName() +"$EncodeAdvice1"
    );
  }

  public static class EncodeAdvice1 {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getLogger(){
      System.out.println("------------------into -------------- EncodeAdvice");
      System.out.println("---------------- root log 初始化完成 ------------------------");
    }
  }
}
