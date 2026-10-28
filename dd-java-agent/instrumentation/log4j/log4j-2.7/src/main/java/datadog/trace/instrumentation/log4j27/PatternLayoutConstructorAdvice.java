package datadog.trace.instrumentation.log4j27;

import datadog.trace.api.Config;
import datadog.trace.bootstrap.instrumentation.log.LogPatternInjector;
import net.bytebuddy.asm.Advice;
import org.apache.logging.log4j.core.layout.PatternLayout;

/** Rewrites the event pattern before Log4j compiles its converters. */
public class PatternLayoutConstructorAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(@Advice.Argument(value = 2, readOnly = false) String pattern) {
    if (Config.get().isLogPatternReplace()) {
      pattern = Config.get().getLogPattern();
    } else if (Config.get().isLogPatternInject()) {
      LogPatternInjector.Format format = LogPatternInjector.Format.LOG4J2;
      pattern =
          LogPatternInjector.injectLog4j(
              pattern == null ? PatternLayout.DEFAULT_CONVERSION_PATTERN : pattern, format);
    }
  }
}
