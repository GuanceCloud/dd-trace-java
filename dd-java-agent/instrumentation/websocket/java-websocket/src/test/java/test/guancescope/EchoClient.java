package test.guancescope;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class EchoClient extends WebSocketClient {
  public final CountDownLatch opened = new CountDownLatch(1),
      received = new CountDownLatch(1),
      closed = new CountDownLatch(1);
  public volatile String message;
  public volatile Exception failure;

  public EchoClient(URI uri) {
    super(uri);
  }

  public void onOpen(ServerHandshake handshake) {
    opened.countDown();
  }

  public void onMessage(String message) {
    this.message = message;
    received.countDown();
  }

  public void onClose(int code, String reason, boolean remote) {
    closed.countDown();
  }

  public void onError(Exception exception) {
    failure = exception;
  }
}
