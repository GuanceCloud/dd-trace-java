package test.guancescope;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;

import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.net.URI;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class Client extends WebSocketClient {
  public RuntimeException failure;
  public volatile AgentSpan openSpan;
  public int errors;

  public Client() {
    super(URI.create("ws://localhost:12345"));
  }

  @Override
  public void connect() {
    if (failure != null) throw failure;
  }

  public void onOpen(ServerHandshake handshake) {
    openSpan = activeSpan();
  }

  public void onMessage(String message) {}

  public void onClose(int code, String reason, boolean remote) {}

  public void onError(Exception exception) {
    errors++;
  }
}
