package datadog.trace.instrumentation.springamqp;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.instrumentation.springamqp.RabbitListenerDecorator.DECORATE;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.java.concurrent.State;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.TreeMap;
import net.bytebuddy.asm.Advice;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.support.Delivery;

@AutoService(InstrumenterModule.class)
public class BlockingQueueConsumerInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {
  public BlockingQueueConsumerInstrumentation() {
    super("spring-rabbit");
  }

  @Override
  public String instrumentedType() {
    return "org.springframework.amqp.rabbit.listener.BlockingQueueConsumer";
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {packageName + ".RabbitListenerDecorator"};
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        named("handle")
            .and(takesArgument(0, named("org.springframework.amqp.rabbit.support.Delivery"))),
        getClass().getName() + "$TransferState");
  }

  @Override
  public Map<String, String> contextStore() {
    Map<String, String> contextStore = new TreeMap<>();
    contextStore.put("org.springframework.amqp.core.Message", State.class.getName());
    contextStore.put("org.springframework.amqp.core.MessageProperties", InetSocketAddress.class.getName());
    contextStore.put("org.springframework.amqp.rabbit.support.Delivery", State.class.getName());
    return contextStore;
  }

  public static class TransferState {
    @Advice.OnMethodExit
    public static void transfer(
        @Advice.This Object consumer,
        @Advice.Argument(0) Delivery delivery,
        @Advice.Return Message message) {
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
      if (null != delivery) {
        ContextStore<Delivery, State> from =
            InstrumentationContext.get(Delivery.class, State.class);
        State state = from.get(delivery);
        if (null != state) {
          from.put(delivery, null);
          InstrumentationContext.get(Message.class, State.class).put(message, state);
        }
      }
    }

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

    public static Object invokeNoArg(Object target, String methodName) {
      if (target == null) {
        return null;
      }
      Class<?> current = target.getClass();
      while (current != null) {
        try {
          Method method = current.getDeclaredMethod(methodName);
          method.setAccessible(true);
          return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
        }
        current = current.getSuperclass();
      }
      return null;
    }

    public static Object readField(Object target, String fieldName) {
      if (target == null) {
        return null;
      }
      Class<?> current = target.getClass();
      while (current != null) {
        try {
          Field field = current.getDeclaredField(fieldName);
          field.setAccessible(true);
          return field.get(target);
        } catch (ReflectiveOperationException ignored) {
        }
        current = current.getSuperclass();
      }
      return null;
    }
  }
}
