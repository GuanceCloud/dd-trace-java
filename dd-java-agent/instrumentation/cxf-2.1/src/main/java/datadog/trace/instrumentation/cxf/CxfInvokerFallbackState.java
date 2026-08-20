package datadog.trace.instrumentation.cxf;

import datadog.context.ContextScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;

public final class CxfInvokerFallbackState {
  private final ContextScope scope;
  private final AgentSpan createdSpan;

  public CxfInvokerFallbackState(ContextScope scope, AgentSpan createdSpan) {
    this.scope = scope;
    this.createdSpan = createdSpan;
  }

  public ContextScope getScope() {
    return scope;
  }

  public AgentSpan getCreatedSpan() {
    return createdSpan;
  }
}
