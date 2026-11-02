package test.guancescope;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public class EchoServer extends WebSocketServer {
  public final CountDownLatch started = new CountDownLatch(1);

  public EchoServer() {
    super(new InetSocketAddress("localhost", 0), 1);
  }

  public void onOpen(WebSocket connection, ClientHandshake handshake) {}

  public void onMessage(WebSocket connection, String message) {
    connection.send("echo:" + message);
  }

  public void onClose(WebSocket connection, int code, String reason, boolean remote) {}

  public void onError(WebSocket connection, Exception exception) {}

  public void onStart() {
    started.countDown();
  }
}
