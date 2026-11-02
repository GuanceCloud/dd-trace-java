package datadog.trace.instrumentation.thrift;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datadog.context.ContextScope;
import datadog.trace.agent.test.AbstractInstrumentationTest;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import org.apache.thrift.TBase;
import org.apache.thrift.TException;
import org.apache.thrift.TFieldIdEnum;
import org.apache.thrift.TMultiplexedProcessor;
import org.apache.thrift.TProcessor;
import org.apache.thrift.TServiceClient;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TMap;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolUtil;
import org.apache.thrift.protocol.TStruct;
import org.apache.thrift.protocol.TType;
import org.apache.thrift.transport.TMemoryBuffer;
import org.junit.jupiter.api.Test;

class ThriftScopeTest extends AbstractInstrumentationTest {
  @Test
  void onewayAndSendFailureRestoreParent() throws Exception {
    AgentSpan parent = startSpan("test", "parent");
    try (ContextScope ignored = activateSpan(parent)) {
      Client client = new Client(new TBinaryProtocol(new TMemoryBuffer(512)));
      client.oneway();
      assertSame(parent, activeSpan());
      TException failure = new TException("write failed");
      Client failing =
          new Client(
              new TBinaryProtocol(new TMemoryBuffer(512)) {
                @Override
                public void writeMessageBegin(TMessage message) throws TException {
                  throw failure;
                }
              });
      assertSame(failure, assertThrows(TException.class, failing::send));
      assertSame(parent, activeSpan());
    } finally {
      parent.finish();
    }
  }

  @Test
  void separateSendAndReceiveDoNotLeaveAnActiveScope() throws Exception {
    TMemoryBuffer buffer = new TMemoryBuffer(1024);
    TBinaryProtocol reply = new TBinaryProtocol(buffer);
    reply.writeMessageBegin(new TMessage("ping", TMessageType.REPLY, 1));
    new Args().write(reply);
    reply.writeMessageEnd();
    Client client =
        new Client(new TBinaryProtocol(buffer), new TBinaryProtocol(new TMemoryBuffer(1024)));
    AgentSpan parent = startSpan("test", "parent");
    try (ContextScope ignored = activateSpan(parent)) {
      client.send();
      assertSame(parent, activeSpan());
      client.receive();
      assertSame(parent, activeSpan());
    } finally {
      parent.finish();
    }
    assertNull(activeSpan());
  }

  @Test
  void noHeaderProcessorAndMultiplexRegistrationRestoreParent() throws Exception {
    AgentSpan parent = startSpan("test", "parent");
    try (ContextScope ignored = activateSpan(parent)) {
      TProcessor processor = new EmptyProcessor();
      assertTrue(processor.process(null, null));
      assertSame(parent, activeSpan());
      new TMultiplexedProcessor().registerProcessor("service", processor);
      assertSame(parent, activeSpan());
    } finally {
      parent.finish();
    }
  }

  @Test
  void duplicateTracingHeadersOpenOnlyOneScope() throws Exception {
    TMemoryBuffer buffer = new TMemoryBuffer(1024);
    TBinaryProtocol out = new TBinaryProtocol(buffer);
    out.writeMessageBegin(new TMessage("ping", TMessageType.CALL, 1));
    out.writeStructBegin(new TStruct("Args"));
    for (int i = 0; i < 2; i++) {
      out.writeFieldBegin(new TField("DD_MAGIC_FIELD", TType.MAP, (short) 8888));
      out.writeMapBegin(new TMap(TType.STRING, TType.STRING, 2));
      out.writeString("x-datadog-trace-id");
      out.writeString("123");
      out.writeString("x-datadog-parent-id");
      out.writeString("456");
      out.writeMapEnd();
      out.writeFieldEnd();
    }
    out.writeFieldStop();
    out.writeStructEnd();
    out.writeMessageEnd();
    ServerInProtocolWrapper in = new ServerInProtocolWrapper(new TBinaryProtocol(buffer));
    in.initial(new Context(null));
    AgentSpan parent = startSpan("test", "parent");
    try (ContextScope ignored = activateSpan(parent)) {
      in.readMessageBegin();
      in.readStructBegin();
      assertEquals(TType.STOP, in.readFieldBegin().type);
      ThriftServerDecorator.SERVER_DECORATOR.finish(null);
      assertSame(parent, activeSpan());
      assertNull(ThriftConstants.CONTEXT_THREAD.get());
    } finally {
      parent.finish();
    }
    writer.waitForTraces(2);
    assertEquals(1, writer.get(0).size());
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void nestedProcessorsRestoreContextAndParent(boolean failInner) throws Exception {
    test.guancescope.NestedProcessor outer = new test.guancescope.NestedProcessor();
    test.guancescope.NestedProcessor inner = new test.guancescope.NestedProcessor();
    RuntimeException failure = new IllegalStateException("inner failed");
    inner.failure = failInner ? failure : null;
    outer.nested = inner;
    outer.nestedInput = tracedInput();
    AgentSpan parent = startSpan("test", "parent");
    try (ContextScope ignored = activateSpan(parent)) {
      if (failInner)
        assertSame(
            failure,
            assertThrows(IllegalStateException.class, () -> outer.process(tracedInput(), null)));
      else assertTrue(outer.process(tracedInput(), null));
      assertTrue(outer.restoredOuter);
      assertSame(parent, activeSpan());
      assertNull(ThriftConstants.CONTEXT_THREAD.get());
    } finally {
      parent.finish();
    }
    writer.waitForTraces(3);
  }

  static ServerInProtocolWrapper tracedInput() throws Exception {
    TMemoryBuffer buffer = new TMemoryBuffer(1024);
    TBinaryProtocol out = new TBinaryProtocol(buffer);
    out.writeMessageBegin(new TMessage("ping", TMessageType.CALL, 1));
    out.writeStructBegin(new TStruct("Args"));
    out.writeFieldBegin(new TField("DD_MAGIC_FIELD", TType.MAP, (short) 8888));
    out.writeMapBegin(new TMap(TType.STRING, TType.STRING, 2));
    out.writeString("x-datadog-trace-id");
    out.writeString("123");
    out.writeString("x-datadog-parent-id");
    out.writeString("456");
    out.writeMapEnd();
    out.writeFieldEnd();
    out.writeFieldStop();
    out.writeStructEnd();
    out.writeMessageEnd();
    return new ServerInProtocolWrapper(new TBinaryProtocol(buffer));
  }

  public static class EmptyProcessor implements TProcessor {
    public boolean process(TProtocol in, TProtocol out) {
      return true;
    }
  }

  public static class Client extends TServiceClient {
    Client(TProtocol protocol) {
      super(protocol);
    }

    Client(TProtocol in, TProtocol out) {
      super(in, out);
    }

    void oneway() throws TException {
      sendBaseOneway("ping", new Args());
    }

    void send() throws TException {
      sendBase("ping", new Args());
    }

    void receive() throws TException {
      receiveBase(new Args(), "ping");
    }
  }

  enum Field implements TFieldIdEnum {
    VALUE;

    public short getThriftFieldId() {
      return 1;
    }

    public String getFieldName() {
      return "value";
    }
  }

  public static class Args implements TBase<Args, Field> {
    public void write(TProtocol protocol) throws TException {
      protocol.writeStructBegin(new TStruct("Args"));
      protocol.writeFieldStop();
      protocol.writeStructEnd();
    }

    public void read(TProtocol protocol) throws TException {
      protocol.readStructBegin();
      while (true) {
        TField field = protocol.readFieldBegin();
        if (field.type == TType.STOP) break;
        TProtocolUtil.skip(protocol, field.type);
        protocol.readFieldEnd();
      }
      protocol.readStructEnd();
    }

    public Field fieldForId(int id) {
      return id == 1 ? Field.VALUE : null;
    }

    public boolean isSet(Field field) {
      return false;
    }

    public Object getFieldValue(Field field) {
      return null;
    }

    public void setFieldValue(Field field, Object value) {}

    public Args deepCopy() {
      return new Args();
    }

    public void clear() {}

    public int compareTo(Args args) {
      return 0;
    }
  }
}
