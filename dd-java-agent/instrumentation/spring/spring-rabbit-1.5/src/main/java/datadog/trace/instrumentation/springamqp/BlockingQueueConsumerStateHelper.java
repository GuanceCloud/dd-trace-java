package datadog.trace.instrumentation.springamqp;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.instrumentation.springamqp.RabbitListenerDecorator.DECORATE;

import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.java.concurrent.State;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.support.Delivery;

public final class BlockingQueueConsumerStateHelper {

  private BlockingQueueConsumerStateHelper() {}

  public static void transfer(Object consumer, Delivery delivery, Message message) {
    InetSocketAddress connection = extractConnection(consumer);
    if (connection != null) {
      AgentSpan span = activeSpan();
      if (span != null) {
        DECORATE.onPeerConnection(span, connection);
      }
      if (message != null) {
        MessageProperties properties = message.getMessageProperties();
        if (properties != null) {
          InstrumentationContext.get(MessageProperties.class, InetSocketAddress.class)
              .put(properties, connection);
        }
      }
    }
    if (delivery != null && message != null) {
      ContextStore<Delivery, State> from = InstrumentationContext.get(Delivery.class, State.class);
      State state = from.get(delivery);
      if (state != null) {
        from.put(delivery, null);
        InstrumentationContext.get(Message.class, State.class).put(message, state);
      }
    }
  }

  private static InetSocketAddress extractConnection(Object consumer) {
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
