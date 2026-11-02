package datadog.trace.instrumentation.websocket.org;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datadog.context.ContextScope;
import datadog.trace.agent.test.AbstractInstrumentationTest;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.test.junit.utils.config.WithConfig;
import java.net.URI;
import org.java_websocket.handshake.HandshakeImpl1Server;
import org.junit.jupiter.api.Test;
import test.guancescope.Client;

@WithConfig(key = "trace.websocket.messages.enabled", value = "true")
class WebsocketScopeTest extends AbstractInstrumentationTest {
  @Test
  void connectAndHandshakeCallbacksRestoreParents() throws Exception {
    Client client = new Client();
    AgentSpan parent = startSpan("test", "parent");
    try (ContextScope ignored = activateSpan(parent)) {
      client.connect();
      assertSame(parent, activeSpan());
    } finally {
      parent.finish();
    }
    HandshakeImpl1Server handshake = new HandshakeImpl1Server();
    handshake.setHttpStatus((short) 101);
    Thread thread = new Thread(() -> client.onWebsocketOpen(client.getConnection(), handshake));
    thread.start();
    thread.join(5000);
    assertFalse(thread.isAlive());
    assertNotNull(client.openSpan);
    assertNull(activeSpan());
  }

  @Test
  void connectErrorPreservesExceptionAndRestoresParent() {
    Client client = new Client();
    client.failure = new IllegalStateException("connect failed");
    AgentSpan parent = startSpan("test", "parent");
    try (ContextScope ignored = activateSpan(parent)) {
      assertSame(client.failure, assertThrows(IllegalStateException.class, client::connect));
      assertSame(parent, activeSpan());
    } finally {
      parent.finish();
    }
  }

  @Test
  void asynchronousErrorFinishesHandshakeWithoutLeaking() {
    Client client = new Client();
    client.connect();
    assertNull(activeSpan());
    client.onWebsocketError(client.getConnection(), new IllegalStateException("handshake failed"));
    assertNull(activeSpan());
    assertEquals(1, client.errors);
  }

  @Test
  void realEchoConnectionPreservesMessagesAndCloseLifecycle() throws Exception {
    test.guancescope.EchoServer server = new test.guancescope.EchoServer();
    server.start();
    test.guancescope.EchoClient client = null;
    try {
      assertTrue(server.started.await(5, java.util.concurrent.TimeUnit.SECONDS));
      client =
          new test.guancescope.EchoClient(
              URI.create("ws://localhost:" + server.getPort() + "/echo"));
      AgentSpan parent = startSpan("test", "parent");
      try (ContextScope ignored = activateSpan(parent)) {
        client.connect();
        assertSame(parent, activeSpan());
      } finally {
        parent.finish();
      }
      assertTrue(client.opened.await(5, java.util.concurrent.TimeUnit.SECONDS));
      client.send("hello");
      assertTrue(client.received.await(5, java.util.concurrent.TimeUnit.SECONDS));
      assertEquals("echo:hello", client.message);
      client.close();
      assertTrue(client.closed.await(5, java.util.concurrent.TimeUnit.SECONDS));
      assertNull(client.failure);
      assertNull(activeSpan());
    } finally {
      if (client != null) client.closeBlocking();
      server.stop(1000);
    }
  }
}
