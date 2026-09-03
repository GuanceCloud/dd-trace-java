package test.websocket.tyrus;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;

import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class TestEndpoint extends Endpoint {
  public final CountDownLatch opened = new CountDownLatch(1);
  public final CountDownLatch messageReceived = new CountDownLatch(1);
  public final CountDownLatch closed = new CountDownLatch(1);
  public final AtomicReference<AgentSpan> onOpenSpan = new AtomicReference<>();
  public final AtomicReference<AgentSpan> onMessageSpan = new AtomicReference<>();
  public final AtomicReference<String> message = new AtomicReference<>();

  @Override
  public void onOpen(Session session, EndpointConfig endpointConfig) {
    onOpenSpan.set(activeSpan());
    session.addMessageHandler(String.class, new TextMessageHandler(this));
    opened.countDown();
  }

  @Override
  public void onClose(Session session, CloseReason closeReason) {
    closed.countDown();
  }

  public static class TextMessageHandler implements MessageHandler.Whole<String> {
    private final TestEndpoint endpoint;

    public TextMessageHandler(TestEndpoint endpoint) {
      this.endpoint = endpoint;
    }

    @Override
    public void onMessage(String message) {
      endpoint.onMessageSpan.set(activeSpan());
      endpoint.message.set(message);
      endpoint.messageReceived.countDown();
    }
  }
}
