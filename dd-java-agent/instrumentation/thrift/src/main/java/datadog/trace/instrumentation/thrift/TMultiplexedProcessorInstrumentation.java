package datadog.trace.instrumentation.thrift;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.instrumentation.thrift.ThriftConstants.INSTRUMENTATION_NAME;
import static datadog.trace.instrumentation.thrift.ThriftConstants.INSTRUMENTATION_NAME_SERVER;
import static datadog.trace.instrumentation.thrift.ThriftConstants.TM_M;
import static datadog.trace.instrumentation.thrift.ThriftConstants.T_MULTIPLEXED_PROCESSOR;
import static datadog.trace.instrumentation.thrift.ThriftServerDecorator.SERVER_DECORATOR;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import org.apache.thrift.ProcessFunction;
import org.apache.thrift.TMultiplexedProcessor;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TProtocol;

@AutoService(InstrumenterModule.class)
public class TMultiplexedProcessorInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {

  public TMultiplexedProcessorInstrumentation() {
    super(INSTRUMENTATION_NAME, INSTRUMENTATION_NAME_SERVER);
  }

  @Override
  public String instrumentedType() {
    return T_MULTIPLEXED_PROCESSOR;
  }

  @Override
  public void methodAdvice(MethodTransformer transformation) {
    transformation.applyAdvice(
        isConstructor(), getClass().getName() + "$TMultiplexedProcessorConstructorAdvice");

    transformation.applyAdvice(
        isMethod().and(isPublic()).and(named("process")),
        getClass().getName() + "$TMultiplexedProcessorProcessAdvice");
    transformation.applyAdvice(
        isMethod().and(isPublic()).and(named("registerProcessor")),
        getClass().getName() + "$TMultiplexedProcessorRegisterProcessAdvice");
    // 0.12以上版本
    transformation.applyAdvice(
        isMethod().and(isPublic()).and(named("registerDefault")),
        getClass().getName() + "$TMultiplexedProcessorRegisterDefaultAdvice");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".ThriftConstants",
      packageName + ".ThriftBaseDecorator",
      packageName + ".ThriftConstants$Tags",
      packageName + ".AbstractContext",
      packageName + ".ServerInProtocolWrapper",
      packageName + ".ExtractAdepter",
      packageName + ".CTProtocolFactory",
      packageName + ".STProtocolFactory",
      packageName + ".ThriftServerDecorator",
      packageName + ".Context"
    };
  }

  public static class TMultiplexedProcessorConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void after(@Advice.This final TMultiplexedProcessor processor) {
      TM_M.put(processor, new HashMap<String, ProcessFunction>());
    }
  }

  public static class TMultiplexedProcessorProcessAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AbstractContext onEnter(
        @Advice.This final TMultiplexedProcessor processor,
        @Advice.AllArguments final Object[] args) {
      try {
        TProtocol protocol = (TProtocol) args[0];
        return ((ServerInProtocolWrapper) protocol).initial(new Context(TM_M.get(processor)));
      } catch (Exception e) {
        throw e;
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Enter AbstractContext context, @Advice.Thrown Throwable throwable) {
      SERVER_DECORATOR.finish(context, throwable);
    }
  }

  public static class TMultiplexedProcessorRegisterProcessAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This final TMultiplexedProcessor obj,
        @Advice.AllArguments final Object[] allArguments) {
      Map<String, ProcessFunction> processMap = TM_M.get(obj);
      String serviceName = (String) allArguments[0];
      TProcessor processor = (TProcessor) allArguments[1];
      processMap.putAll(ThriftConstants.getProcessMap(serviceName, processor));
      TM_M.put(obj, processMap);
    }
  }

  public static class TMultiplexedProcessorRegisterDefaultAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This final TMultiplexedProcessor obj,
        @Advice.AllArguments final Object[] allArguments) {
      Map<String, ProcessFunction> processMap = TM_M.get(obj);
      TProcessor processor = (TProcessor) allArguments[0];
      processMap.putAll(ThriftConstants.getProcessMap(processor));
      TM_M.put(obj, processMap);
    }
  }
}
