package datadog.trace.instrumentation.thrift;

import static datadog.trace.bootstrap.instrumentation.api.AgentPropagation.extractContextAndGetSpanContext;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;
import static datadog.trace.instrumentation.thrift.ExtractAdepter.GETTER;
import static datadog.trace.instrumentation.thrift.ThriftConstants.INSTRUMENTATION_NAME;
import static datadog.trace.instrumentation.thrift.ThriftConstants.THRIFT;
import static datadog.trace.instrumentation.thrift.ThriftConstants.THRIFT_SERVER_COMPONENT;

import datadog.context.ContextScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.AgentSpanContext;
import java.util.Map;
import java.util.Optional;

public class ThriftServerDecorator extends ThriftBaseDecorator {
  public static final ThriftServerDecorator SERVER_DECORATOR = new ThriftServerDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {INSTRUMENTATION_NAME};
  }

  @Override
  protected CharSequence spanType() {
    return THRIFT;
  }

  @Override
  protected CharSequence component() {
    return THRIFT_SERVER_COMPONENT;
  }

  @Override
  public CharSequence spanName() {
    return component();
  }

  public AgentSpan createSpan(Map<String, String> header, AbstractContext context) {
    AgentSpanContext parentContext = extractContextAndGetSpanContext(header, GETTER);
    //    AgentSpan span = startSpan(spanName(),parentContext,context.startTime);
    AgentSpan span = startSpan("thrift", spanName(), parentContext);
    withMethod(span, context.methodName);
    withResource(
        span,
        Optional.ofNullable(context.getOperatorName()).isPresent()
            ? context.getOperatorName()
            : context.methodName);
    if (Optional.ofNullable(context.getArguments()).isPresent()) {
      span.setTag(ThriftConstants.Tags.ARGS, context.getArguments());
    }
    afterStart(span);
    return span;
  }

  public void finish(Throwable throwable) {
    finish(ThriftConstants.CONTEXT_THREAD.get(), throwable);
  }

  public void finish(AbstractContext context, Throwable throwable) {
    if (context == null) {
      return;
    }
    if (ThriftConstants.CONTEXT_THREAD.get() == context) {
      AbstractContext previous = context.getPrevious();
      if (previous == null) {
        ThriftConstants.CONTEXT_THREAD.remove();
      } else {
        ThriftConstants.CONTEXT_THREAD.set(previous);
      }
    }
    context.setPrevious(null);
    if (context.getAgentScope() == null) {
      return;
    }
    ContextScope scope = context.getAgentScope();
    context.setAgentScope(null);
    AgentSpan span = spanFromScope(scope);
    try {
      onError(span, throwable);
      beforeFinish(span);
    } finally {
      scope.close();
      span.finish();
    }
  }
}
