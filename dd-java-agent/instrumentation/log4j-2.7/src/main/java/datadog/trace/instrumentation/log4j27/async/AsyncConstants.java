package datadog.trace.instrumentation.log4j27.async;

import java.lang.reflect.Field;

public class AsyncConstants {

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
