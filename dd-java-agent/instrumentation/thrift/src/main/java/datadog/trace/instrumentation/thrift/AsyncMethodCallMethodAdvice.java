package datadog.trace.instrumentation.thrift;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.checkpointActiveForRollback;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.rollbackActiveToCheckpoint;
import static datadog.trace.instrumentation.thrift.ThriftClientDecorator.CLIENT_DECORATOR;

import datadog.context.ContextScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import net.bytebuddy.asm.Advice;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncMethodCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncMethodCallMethodAdvice {
  public static final Logger logger = LoggerFactory.getLogger(AsyncMethodCallMethodAdvice.class);

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static ContextScope onEnter(
      @Advice.This final TAsyncMethodCall methodCall,
      @Advice.AllArguments final Object[] args,
      @Advice.FieldValue("callback") final AsyncMethodCallback<Object> callback) {
    AgentSpan agentSpan = CLIENT_DECORATOR.createSpan(methodCall.getClass().getName(), null);
    ContextScope scope = activateSpan(agentSpan);
    try {
      ThriftConstants.setValue(
          TAsyncMethodCall.class,
          methodCall,
          "callback",
          new DataDogAsyncMethodCallback<Object>(callback, agentSpan));
    } catch (Exception e) {
      if (logger.isDebugEnabled()) {
        logger.debug("set value callback fail", e);
      }
      logger.error("set value callback fail", e);
    }
    checkpointActiveForRollback();
    return scope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(@Advice.Enter final ContextScope scope) {
    rollbackActiveToCheckpoint();
    if (scope != null) {
      scope.close();
    }
  }
}
