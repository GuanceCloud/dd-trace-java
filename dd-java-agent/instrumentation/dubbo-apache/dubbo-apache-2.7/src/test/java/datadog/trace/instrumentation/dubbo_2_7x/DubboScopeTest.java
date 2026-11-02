package datadog.trace.instrumentation.dubbo_2_7x;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import datadog.context.ContextScope;
import datadog.trace.agent.test.AbstractInstrumentationTest;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.RpcResult;
import org.apache.dubbo.rpc.protocol.AbstractInvoker;
import org.junit.jupiter.api.Test;

class DubboScopeTest extends AbstractInstrumentationTest {
  @Test
  void thrownRpcExceptionRestoresParent() throws Exception {
    RpcException failure = new RpcException("network failed");
    AgentSpan parent = startSpan("test", "parent");
    try (ContextScope ignored = activateSpan(parent)) {
      assertSame(
          failure,
          assertThrows(RpcException.class, () -> new TestInvoker(failure).invoke(invocation())));
      assertSame(parent, activeSpan());
    } finally {
      parent.finish();
    }
    writer.waitForTraces(1);
    assertEquals(2, writer.get(0).size());
  }

  @Test
  void normalResultIsUnchangedAndParentIsRestored() throws Exception {
    AgentSpan parent = startSpan("test", "parent");
    try (ContextScope ignored = activateSpan(parent)) {
      assertEquals("pong", new TestInvoker(null).invoke(invocation()).getValue());
      assertSame(parent, activeSpan());
    } finally {
      parent.finish();
    }
    writer.waitForTraces(1);
    assertEquals(2, writer.get(0).size());
  }

  static RpcInvocation invocation() {
    return new RpcInvocation("ping", new Class<?>[0], new Object[0]);
  }

  public interface Service {
    String ping();
  }

  public static class TestInvoker extends AbstractInvoker<Service> {
    final RpcException failure;

    TestInvoker(RpcException failure) {
      super(
          Service.class,
          new URL("dubbo", "localhost", 20880, Service.class.getName())
              .addParameter("side", "consumer"));
      this.failure = failure;
    }

    protected Result doInvoke(Invocation invocation) {
      if (failure != null) throw failure;
      return new RpcResult("pong");
    }
  }
}
