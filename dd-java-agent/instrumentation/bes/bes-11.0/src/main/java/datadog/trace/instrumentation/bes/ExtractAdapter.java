package datadog.trace.instrumentation.bes;

import com.bes.enterprise.web.crane.Request;
import com.bes.enterprise.web.util.buf.MessageBytes;
import com.bes.enterprise.web.util.http.MimeHeaders;
import com.bes.enterprise.webtier.connector.Response;
import datadog.trace.bootstrap.instrumentation.api.AgentPropagation;

public abstract class ExtractAdapter<T> implements AgentPropagation.ContextVisitor<T> {
  abstract MimeHeaders getMimeHeaders(T carrier);

  static String messageBytesToString(final MessageBytes messageBytes) {
    if (messageBytes == null) {
      return null;
    }
    switch (messageBytes.getType()) {
      case MessageBytes.T_BYTES:
        return messageBytes.getByteChunk().toString();
      case MessageBytes.T_CHARS:
        return messageBytes.getCharChunk().toString();
      default:
        return messageBytes.toString();
    }
  }

  @Override
  public void forEachKey(final T carrier, final AgentPropagation.KeyClassifier classifier) {
    MimeHeaders headers = getMimeHeaders(carrier);
    if (headers == null) {
      return;
    }
    for (int i = 0; i < headers.size(); ++i) {
      MessageBytes header = headers.getName(i);
      MessageBytes value = headers.getValue(i);
      String headerName = messageBytesToString(header);
      if (headerName == null) {
        continue;
      }
      if (!classifier.accept(headerName, messageBytesToString(value))) {
        return;
      }
    }
  }

  public static final class CraneRequest extends ExtractAdapter<Request> {
    public static final CraneRequest GETTER = new CraneRequest();

    @Override
    MimeHeaders getMimeHeaders(final Request request) {
      if (request == null) {
        return null;
      }
      return request.getMimeHeaders();
    }
  }

  public static final class ConnectorResponse extends ExtractAdapter<Response> {
    public static final ConnectorResponse GETTER = new ConnectorResponse();

    @Override
    MimeHeaders getMimeHeaders(final Response response) {
      if (response == null || response.getCoyoteResponse() == null) {
        return null;
      }
      return response.getCoyoteResponse().getMimeHeaders();
    }
  }
}
