package test.guancescope;

import org.apache.axis.MessageContext;
import org.apache.axis.handlers.BasicHandler;

public class Handler extends BasicHandler {
  public RuntimeException failure;
  public int calls;

  public void invoke(MessageContext message) {
    calls++;
    if (failure != null) throw failure;
    new NestedHandler().invoke(message);
  }
}
