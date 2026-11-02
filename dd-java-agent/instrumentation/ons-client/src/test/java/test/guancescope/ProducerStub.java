package test.guancescope;

import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.Producer;
import com.aliyun.openservices.ons.api.SendCallback;
import com.aliyun.openservices.ons.api.SendResult;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

public class ProducerStub implements Producer {
  public boolean inline;
  public SendCallback pending;
  public RuntimeException failure;

  public void sendAsync(Message message, SendCallback callback) {
    if (failure != null) throw failure;
    pending = callback;
    if (inline) callback.onSuccess(new SendResult());
  }

  public SendResult send(Message message) {
    return new SendResult();
  }

  public void sendOneway(Message message) {}

  public void start() {}

  public void shutdown() {}

  public boolean isStarted() {
    return true;
  }

  public boolean isClosed() {
    return false;
  }

  public void updateCredential(Properties properties) {}

  public void setCallbackExecutor(ExecutorService executor) {}
}
