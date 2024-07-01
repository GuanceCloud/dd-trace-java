package datadog.trace.instrumentation.log4j27.async;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.api.*;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import net.bytebuddy.asm.Advice;
import org.apache.logging.log4j.core.async.RingBufferLogEventTranslator;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ObjectMessage;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.apache.logging.log4j.util.StringMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

@AutoService(InstrumenterModule.class)
public class RingBufferLogEventTranslatorInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForSingleType {

  public RingBufferLogEventTranslatorInstrumentation() {
    super("log4j2","logevent");
  }

  @Override
  public String instrumentedType() {
    return "org.apache.logging.log4j.core.async.RingBufferLogEventTranslator";
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {packageName + ".AsyncConstants"};
  }

  @Override
  public void methodAdvice(MethodTransformer transformation) {
    transformation.applyAdvice(isMethod().and(named("translateTo")), RingBufferLogEventTranslatorInstrumentation.class.getName()  + "$RingBufferLogEventTranslatorAdvice");

  }

  public static final class RingBufferLogEventTranslatorAdvice {
    @Advice.OnMethodEnter
    public static void before(@Advice.This RingBufferLogEventTranslator obj,@Advice.FieldValue("message") Message message) {
      String data = message.getFormattedMessage();
      AgentSpan span = activeSpan();
      ObjectMessage objectMessage;

//      String tracePattern = " dd.service dd.trace_id dd.span_id ";
      String tracePattern = Config.get().getLog4j2TraceAsyncPattern();
      if (tracePattern==null){
        return;
      }
      Config config = Config.get();
      String env = Optional.ofNullable(config.getEnv()).orElse("");
      String serviceName = Optional.ofNullable(config.getServiceName()).orElse("");
      String version = Optional.ofNullable(config.getVersion()).orElse("");

      tracePattern = tracePattern.replace(Tags.DD_ENV, env)
          .replace(Tags.DD_SERVICE, serviceName)
          .replace(Tags.DD_VERSION, version);

      String traceIdValue;
      if (span != null) {
        DDTraceId traceId = span.context().getTraceId();
        traceIdValue = InstrumenterConfig.get().isLogs128bTraceIdEnabled() && traceId.toHighOrderLong() != 0
            ? traceId.toHexString()
            : traceId.toString();
        tracePattern = tracePattern.replace(CorrelationIdentifier.getTraceIdKey(), traceIdValue)
            .replace(CorrelationIdentifier.getSpanIdKey(), DDSpanId.toString(span.context().getSpanId()));
      }else{
        tracePattern = tracePattern.replace(CorrelationIdentifier.getTraceIdKey(), "")
            .replace(CorrelationIdentifier.getSpanIdKey(),"");
      }

      StringBuilder messageBuilder = new StringBuilder(tracePattern);
      messageBuilder.append(data);

      objectMessage = new ObjectMessage(messageBuilder.toString());

      try {
        AsyncConstants.setValue(RingBufferLogEventTranslator.class, obj, "message", objectMessage);
      }catch (Exception e){
        e.printStackTrace();
      }
    }
  }
}
