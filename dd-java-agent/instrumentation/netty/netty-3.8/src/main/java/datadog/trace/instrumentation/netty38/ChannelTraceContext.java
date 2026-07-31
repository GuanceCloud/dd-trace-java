package datadog.trace.instrumentation.netty38;

import datadog.context.Context;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.websocket.HandlerContext;
import java.io.ByteArrayOutputStream;
import org.jboss.netty.handler.codec.http.HttpHeaders;

public class ChannelTraceContext {
  public static class Factory implements ContextStore.Factory<ChannelTraceContext> {
    public static final Factory INSTANCE = new Factory();

    @Override
    public ChannelTraceContext create() {
      return new ChannelTraceContext();
    }
  }

  AgentScope.Continuation connectionContinuation;
  Context serverContext;
  AgentSpan clientSpan;
  AgentSpan clientParentSpan;
  HttpHeaders requestHeaders;
  String requestUri;
  ByteArrayOutputStream requestBodyBuffer;
  String requestBodyEncoding;
  ByteArrayOutputStream responseBodyBuffer;
  String responseBodyEncoding;
  boolean analyzedResponse;
  boolean blockedResponse;

  HandlerContext.Sender senderHandlerContext;
  HandlerContext.Receiver receiverHandlerContext;

  public void reset() {
    this.connectionContinuation = null;
    this.serverContext = null;
    this.clientSpan = null;
    this.clientParentSpan = null;
    this.requestHeaders = null;
    this.requestUri = null;
    this.requestBodyBuffer = null;
    this.requestBodyEncoding = null;
    this.responseBodyBuffer = null;
    this.responseBodyEncoding = null;
    this.analyzedResponse = false;
    this.blockedResponse = false;
  }

  public void setRequestHeaders(HttpHeaders headers) {
    this.requestHeaders = headers;
  }

  public HttpHeaders getRequestHeaders() {
    return requestHeaders;
  }

  public void setRequestUri(String requestUri) {
    this.requestUri = requestUri;
  }

  public String getRequestUri() {
    return requestUri;
  }

  public void setRequestBody(ByteArrayOutputStream buffer, String encoding) {
    this.requestBodyBuffer = buffer;
    this.requestBodyEncoding = encoding;
  }

  public ByteArrayOutputStream getRequestBodyBuffer() {
    return requestBodyBuffer;
  }

  public String getRequestBodyEncoding() {
    return requestBodyEncoding;
  }

  public void clearRequestBody() {
    this.requestBodyBuffer = null;
    this.requestBodyEncoding = null;
  }

  public void setResponseBody(ByteArrayOutputStream buffer, String encoding) {
    this.responseBodyBuffer = buffer;
    this.responseBodyEncoding = encoding;
  }

  public ByteArrayOutputStream getResponseBodyBuffer() {
    return responseBodyBuffer;
  }

  public String getResponseBodyEncoding() {
    return responseBodyEncoding;
  }

  public void clearResponseBody() {
    this.responseBodyBuffer = null;
    this.responseBodyEncoding = null;
  }

  public boolean isAnalyzedResponse() {
    return analyzedResponse;
  }

  public void setAnalyzedResponse(boolean analyzedResponse) {
    this.analyzedResponse = analyzedResponse;
  }

  public boolean isBlockedResponse() {
    return blockedResponse;
  }

  public void setBlockedResponse(boolean blockedResponse) {
    this.blockedResponse = blockedResponse;
  }

  public AgentScope.Continuation getConnectionContinuation() {
    return connectionContinuation;
  }

  public Context getServerContext() {
    return serverContext;
  }

  public AgentSpan getServerSpan() {
    return AgentSpan.fromContext(serverContext);
  }

  public AgentSpan getClientSpan() {
    return clientSpan;
  }

  public AgentSpan getClientParentSpan() {
    return clientParentSpan;
  }

  public void setConnectionContinuation(AgentScope.Continuation connectionContinuation) {
    this.connectionContinuation = connectionContinuation;
  }

  public void setServerContext(Context serverContext) {
    this.serverContext = serverContext;
  }

  public void setClientSpan(AgentSpan clientSpan) {
    this.clientSpan = clientSpan;
  }

  public void setClientParentSpan(AgentSpan clientParentSpan) {
    this.clientParentSpan = clientParentSpan;
  }

  public HandlerContext.Sender getSenderHandlerContext() {
    return senderHandlerContext;
  }

  public void setSenderHandlerContext(HandlerContext.Sender senderHandlerContext) {
    this.senderHandlerContext = senderHandlerContext;
  }

  public HandlerContext.Receiver getReceiverHandlerContext() {
    return receiverHandlerContext;
  }

  public void setReceiverHandlerContext(HandlerContext.Receiver receiverHandlerContext) {
    this.receiverHandlerContext = receiverHandlerContext;
  }
}
