package datadog.trace.instrumentation.disruptor;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.bootstrap.WeakMap;
import java.util.Collections;
import java.util.Map;

@AutoService(InstrumenterModule.class)
public class RingBufferInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {
  public RingBufferInstrumentation() {
    super("disruptor");
  }

  @Override
  public String instrumentedType() {
    return "com.lmax.disruptor.RingBuffer";
  }

  @Override
  public void methodAdvice(MethodTransformer transformation) {
    transformation.applyAdvice(
        isMethod().and(named("publish")).and(takesArguments(1)),
        packageName + ".RingBufferPublishAdvice");
    transformation.applyAdvice(
        isMethod().and(named("publish")).and(takesArguments(2)),
        packageName + ".RingBufferPublishBatchAdvice");
    transformation.applyAdvice(
        isMethod().and(named("translateAndPublish")), packageName + ".RingBufferTranslateAdvice");
    transformation.applyAdvice(
        isMethod().and(named("translateAndPublishBatch")),
        packageName + ".RingBufferTranslateBatchAdvice");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {packageName + ".DisruptorDecorator"};
  }

  @Override
  public Map<String, String> contextStore() {
    return Collections.singletonMap(Class.class.getName(), WeakMap.class.getName());
  }
}
