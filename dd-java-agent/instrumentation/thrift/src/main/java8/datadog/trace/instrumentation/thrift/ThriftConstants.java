package datadog.trace.instrumentation.thrift;

import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;
import org.apache.thrift.async.TAsyncMethodCall;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

public class ThriftConstants {
  public static final CharSequence THRIFT = UTF8BytesString.create("thrift");

  public static final String INSTRUMENTATION_NAME = "thrift";
  public static final String INSTRUMENTATION_NAME_CLIENT = "thrift-client";
  public static final String INSTRUMENTATION_NAME_SERVER = "thrift-server";
  public static final String TASYNC_CLIENT = "org.apache.thrift.async.TAsyncClient";
  public static final String T_ASYNC_METHOD_CALL = "org.apache.thrift.async.TAsyncMethodCall";
  public static final String TSERVICE_CLIENT = "org.apache.thrift.TServiceClient";
  public static final String T_BASE_PROCESSOR = "org.apache.thrift.TBaseProcessor";
  public static final String T_BASE_ASYNC_PROCESSOR = "org.apache.thrift.TBaseAsyncProcessor";
  public static final String T_SERVER = "org.apache.thrift.server.TServer";

  public static final String DD_MAGIC_FIELD = "DD_MAGIC_FIELD"; // Field Name
  public static final short DD_MAGIC_FIELD_ID = 8888; // Field ID, a magic number

  public static final CharSequence THRIFT_CLIENT_COMPONENT = UTF8BytesString.create(INSTRUMENTATION_NAME_CLIENT);
  public static final CharSequence THRIFT_SERVER_COMPONENT = UTF8BytesString.create(INSTRUMENTATION_NAME_SERVER);

  public static ThreadLocal<AbstractContext> CONTEXT_THREAD =  new ThreadLocal<>();
  public static ThreadLocal<Boolean> CLIENT_INJECT_THREAD =  new ThreadLocal<>();

  interface Tags {
    String ARGS = "args";
    String METHOD = "method";
  }

  public static final void setValue(Class klass, Object instance, String name, Object value) throws NoSuchFieldException, IllegalAccessException {
    Field field = klass.getDeclaredField(name);
    field.setAccessible(true);
    field.set(instance, value);
  }

  public static final Object getValue(Class klass, Object instance, String name) throws NoSuchFieldException, IllegalAccessException {
    Field field = klass.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(instance);
  }
}
