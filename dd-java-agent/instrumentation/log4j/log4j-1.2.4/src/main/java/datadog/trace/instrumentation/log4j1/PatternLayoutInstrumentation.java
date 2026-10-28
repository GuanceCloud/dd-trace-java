package datadog.trace.instrumentation.log4j1;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.api.Config;
import datadog.trace.bootstrap.instrumentation.log.LogPatternInjector;
import net.bytebuddy.asm.Advice;
import org.apache.log4j.spi.LoggingEvent;

@AutoService(InstrumenterModule.class)
public class PatternLayoutInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {
  public PatternLayoutInstrumentation() {
    super("log4j", "log4j-1");
  }

  @Override
  protected boolean defaultEnabled() {
    return Config.get().isLogPatternInject();
  }

  @Override
  public String instrumentedType() {
    return "org.apache.log4j.PatternLayout";
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isConstructor()
            .or(isMethod().and(named("setConversionPattern")))
            .and(takesArguments(String.class)),
        getClass().getName() + "$PatternAdvice");
  }

  public static class PatternAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(value = 0, readOnly = false) String pattern) {
      if (Config.get().isLogPatternInject()) {
        boolean fullMdc = false;
        try {
          // Full-MDC rendering arrived with LoggingEvent.getProperties in later 1.2 releases.
          LoggingEvent.class.getMethod("getProperties");
          fullMdc = true;
        } catch (NoSuchMethodException legacyVersion) {
          // A bare %X on these releases does not output any correlation fields.
        }
        pattern =
            LogPatternInjector.injectLog4j(
                pattern,
                fullMdc
                    ? LogPatternInjector.Format.LOG4J1
                    : LogPatternInjector.Format.LOG4J1_LEGACY);
      }
    }
  }
}
