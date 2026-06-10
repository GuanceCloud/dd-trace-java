package datadog.trace.instrumentation.log4j27;

import datadog.trace.api.Config;
import java.lang.reflect.Field;
import net.bytebuddy.asm.Advice;
import org.apache.logging.log4j.core.layout.PatternLayout;

public class PatternLayoutBuildAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(@Advice.This Object builder) throws Exception {
    Field patternField = PatternLayout.Builder.class.getDeclaredField("pattern");
    patternField.setAccessible(true);
    patternField.set(builder, Config.get().getLogPattern());
  }
}
