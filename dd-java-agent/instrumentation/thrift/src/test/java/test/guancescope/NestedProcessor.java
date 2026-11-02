package test.guancescope;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;

import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import org.apache.thrift.TException;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TProtocol;

public class NestedProcessor implements TProcessor {
  public TProcessor nested;
  public TProtocol nestedInput;
  public RuntimeException failure;
  public boolean restoredOuter;

  public boolean process(TProtocol input, TProtocol output) throws TException {
    input.readMessageBegin();
    input.readStructBegin();
    input.readFieldBegin();
    AgentSpan outer = activeSpan();
    try {
      if (nested != null) nested.process(nestedInput, output);
    } finally {
      restoredOuter = activeSpan() == outer;
    }
    if (failure != null) throw failure;
    input.readStructEnd();
    input.readMessageEnd();
    return true;
  }
}
