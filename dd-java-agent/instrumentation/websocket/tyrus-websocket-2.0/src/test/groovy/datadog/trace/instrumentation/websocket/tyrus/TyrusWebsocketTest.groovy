package datadog.trace.instrumentation.websocket.tyrus

import datadog.trace.agent.test.InstrumentationSpecification
import datadog.trace.api.DDTags
import datadog.trace.bootstrap.instrumentation.api.InternalSpanTypes
import datadog.trace.bootstrap.instrumentation.api.Tags
import datadog.trace.core.DDSpan
import jakarta.websocket.ClientEndpointConfig
import jakarta.websocket.CloseReason
import jakarta.websocket.DeploymentException
import jakarta.websocket.Session
import org.glassfish.tyrus.client.ClientManager
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import spock.util.concurrent.PollingConditions
import test.websocket.tyrus.TestEndpoint

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

class TyrusWebsocketTest extends InstrumentationSpecification {

  @Override
  protected void configurePreAgent() {
    super.configurePreAgent()
    injectSysConfig("dd.integration.java-websocket.enabled", "false")
  }

  def "traces standalone Tyrus client using the Grizzly transport"() {
    setup:
    def server = new EchoServer()
    server.start()
    assert server.started.await(5, TimeUnit.SECONDS)
    Session session

    when:
    def endpoint = new TestEndpoint()
    session = ClientManager.createClient().connectToServer(
      endpoint,
      ClientEndpointConfig.Builder.create().build(),
      URI.create("ws://localhost:${server.port}/echo"))

    then:
    endpoint.opened.await(5, TimeUnit.SECONDS)
    endpoint.onOpenSpan.get() != null
    server.datadogTraceId.get() != null

    when:
    session.basicRemote.sendText("hello")

    then:
    server.messageReceived.await(5, TimeUnit.SECONDS)
    endpoint.messageReceived.await(5, TimeUnit.SECONDS)
    endpoint.message.get() == "echo:hello"
    endpoint.onMessageSpan.get() != null

    when:
    session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "done"))

    then:
    endpoint.closed.await(5, TimeUnit.SECONDS)
    new PollingConditions(timeout: 5).eventually {
      def spans = TEST_WRITER.flatten() as List<DDSpan>
      assert findByOperation(spans, "websocket.open") != null
      assert findByOperation(spans, "websocket.send") != null
      assert findByOperation(spans, "websocket.receive") != null
      assert findByOperation(spans, "websocket.close") != null
    }

    when:
    def allSpans = TEST_WRITER.flatten() as List<DDSpan>
    def handshake = findByOperation(allSpans, "websocket.open")
    def send = findByOperation(allSpans, "websocket.send")
    def receive = findByOperation(allSpans, "websocket.receive")
    def close = findByOperation(allSpans, "websocket.close")

    then:
    handshake.getTag(Tags.HTTP_METHOD) == "GET"
    (handshake.getTag(Tags.HTTP_STATUS) as Number).intValue() == 101
    handshake.getTag(Tags.SPAN_KIND) == Tags.SPAN_KIND_CLIENT
    handshake.getTag("websocket.handshake.success") == true
    handshake.spanType.toString() == InternalSpanTypes.WEBSOCKET.toString()
    handshake.getTag(Tags.HTTP_URL).toString().contains("/echo")
    endpoint.onOpenSpan.get().spanId == handshake.spanId
    server.datadogTraceId.get() == handshake.traceId.toString()
    [send, receive, close].every {
      it.traceId != handshake.traceId &&
        it.parentId == 0 &&
        it.links.size() == 1 &&
        it.links[0].traceId() == handshake.traceId &&
        it.links[0].spanId() == handshake.spanId
    }
    receive.spanType.toString() == InternalSpanTypes.WEBSOCKET.toString()
    receive.getTag("websocket.message.type").toString() == "text"
    (receive.getTag("websocket.message.length") as Number).intValue() == 10

    cleanup:
    if (session != null && session.open) {
      session.close()
    }
    server.stop(1000)
  }

  def "finishes the handshake span when the connection fails"() {
    setup:
    def server = new RejectingServer()

    when:
    ClientManager.createClient().connectToServer(
      new TestEndpoint(),
      ClientEndpointConfig.Builder.create().build(),
      URI.create("ws://127.0.0.1:${server.port}/echo"))

    then:
    thrown(DeploymentException)
    new PollingConditions(timeout: 5).eventually {
      def handshake = findByOperation(TEST_WRITER.flatten() as List<DDSpan>, "websocket.handshake")
      assert handshake != null
      assert handshake.error
      assert (handshake.getTag(Tags.HTTP_STATUS) as Number).intValue() == 400
    }

    cleanup:
    server.close()
  }

  def "records the transport error when the server drops the handshake"() {
    setup:
    def server = new RejectingServer(false)
    def client = ClientManager.createClient()
    client.properties[ClientManager.HANDSHAKE_TIMEOUT] = 250

    when:
    client.connectToServer(
      new TestEndpoint(),
      ClientEndpointConfig.Builder.create().build(),
      URI.create("ws://127.0.0.1:${server.port}/echo"))

    then:
    thrown(DeploymentException)
    new PollingConditions(timeout: 5).eventually {
      def handshake = findByOperation(TEST_WRITER.flatten() as List<DDSpan>, "websocket.handshake")
      assert handshake != null
      assert handshake.error
      assert handshake.getTag(DDTags.ERROR_TYPE) == TimeoutException.name
      assert handshake.getTag(DDTags.ERROR_MSG) == "WebSocket handshake response was not received"
    }

    cleanup:
    server.close()
  }

  private static DDSpan findByOperation(List<DDSpan> spans, String operationName) {
    spans.find { operationName.contentEquals(it.operationName) }
  }

  static class EchoServer extends WebSocketServer {
    final CountDownLatch started = new CountDownLatch(1)
    final CountDownLatch messageReceived = new CountDownLatch(1)
    final AtomicReference<String> datadogTraceId = new AtomicReference<>()

    EchoServer() {
      super(new InetSocketAddress("localhost", 0))
    }

    @Override
    void onOpen(WebSocket connection, ClientHandshake handshake) {
      datadogTraceId.set(handshake.getFieldValue("x-datadog-trace-id"))
    }

    @Override
    void onMessage(WebSocket connection, String message) {
      messageReceived.countDown()
      connection.send("echo:" + message)
    }

    @Override
    void onClose(WebSocket connection, int code, String reason, boolean remote) {}

    @Override
    void onError(WebSocket connection, Exception exception) {}

    @Override
    void onStart() {
      started.countDown()
    }
  }

  static class RejectingServer implements AutoCloseable {
    private final ServerSocket serverSocket = new ServerSocket(0)
    private final Thread serverThread
    private final boolean sendResponse

    RejectingServer(boolean sendResponse = true) {
      this.sendResponse = sendResponse
      serverThread = new Thread({ rejectHandshake() }, "tyrus-rejecting-server")
      serverThread.daemon = true
      serverThread.start()
    }

    int getPort() {
      serverSocket.localPort
    }

    private void rejectHandshake() {
      serverSocket.accept().withCloseable { socket ->
        def reader = socket.inputStream.newReader()
        while (reader.readLine()) {}
        if (sendResponse) {
          socket.outputStream.write(
            "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
            .getBytes("UTF-8"))
          socket.outputStream.flush()
        }
      }
    }

    @Override
    void close() {
      serverSocket.close()
      serverThread.join(TimeUnit.SECONDS.toMillis(5))
    }
  }
}
