package datadog.trace.instrumentation.thrift;

import static datadog.trace.instrumentation.thrift.ThriftServerDecorator.SERVER_DECORATOR;

import net.bytebuddy.asm.Advice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Description @Author lenovo @Date 2022/11/25 11:12
 */
public class TProcessorProcessAdvice {
  public static final Logger logger = LoggerFactory.getLogger(TProcessorProcessAdvice.class);

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AbstractContext onEnter(
      @Advice.This final Object obj, @Advice.AllArguments final Object[] args) {
    logger.info("TProcessorProcessAdvice : " + obj.getClass().getName());
    try {
      Object in = args[0];
      if (in instanceof ServerInProtocolWrapper) {
        return ((ServerInProtocolWrapper) in).initial(new Context(null));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void after(
      @Advice.Enter AbstractContext context, @Advice.Thrown final Throwable throwable) {
    SERVER_DECORATOR.finish(context, throwable);
  }
}
