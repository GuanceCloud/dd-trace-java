package datadog.trace.instrumentation.springamqp;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public final class BlockingQueueConsumerStateHelper {

  private BlockingQueueConsumerStateHelper() {}

  public static InetSocketAddress extractConnection(Object consumer) {
    Object channel = invokeNoArg(consumer, "getChannel");
    if (channel == null) {
      channel = readField(consumer, "channel");
    }
    if (channel == null) {
      return null;
    }
    Object connection = invokeNoArg(channel, "getConnection");
    if (connection == null) {
      return null;
    }
    Integer port = (Integer) invokeNoArg(connection, "getPort");
    Object address = invokeNoArg(connection, "getAddress");
    if (address instanceof InetAddress) {
      return new InetSocketAddress((InetAddress) address, port != null ? port : 0);
    }
    Object host = invokeNoArg(connection, "getHost");
    if (host instanceof String) {
      return InetSocketAddress.createUnresolved((String) host, port != null ? port : 0);
    }
    return null;
  }

  private static Object invokeNoArg(Object target, String methodName) {
    if (target == null) {
      return null;
    }
    Class<?> current = target.getClass();
    while (current != null) {
      try {
        Method method = current.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
      } catch (Throwable ignored) {
      }
      current = current.getSuperclass();
    }
    return null;
  }

  private static Object readField(Object target, String fieldName) {
    if (target == null) {
      return null;
    }
    Class<?> current = target.getClass();
    while (current != null) {
      try {
        Field field = current.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
      } catch (Throwable ignored) {
      }
      current = current.getSuperclass();
    }
    return null;
  }
}
