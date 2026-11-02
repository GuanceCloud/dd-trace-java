package test.guancescope;

import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.order.ConsumeOrderContext;
import com.aliyun.openservices.ons.api.order.MessageOrderListener;
import com.aliyun.openservices.ons.api.order.OrderAction;

public class OrderedListener implements MessageOrderListener {
  public final RuntimeException failure;

  public OrderedListener(RuntimeException failure) {
    this.failure = failure;
  }

  public OrderAction consume(Message message, ConsumeOrderContext context) {
    throw failure;
  }
}
