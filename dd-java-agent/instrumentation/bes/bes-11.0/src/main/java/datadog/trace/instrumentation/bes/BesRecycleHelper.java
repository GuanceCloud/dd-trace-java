package datadog.trace.instrumentation.bes;

import static datadog.trace.bootstrap.instrumentation.decorator.HttpServerDecorator.DD_CONTEXT_ATTRIBUTE;
import static datadog.trace.instrumentation.bes.BesDecorator.finishSpan;

import com.bes.enterprise.webtier.connector.Request;
import com.bes.enterprise.webtier.connector.Response;
import datadog.context.Context;

public final class BesRecycleHelper {

  private BesRecycleHelper() {}

  public static void stopSpan(final Request request) {
    if (request == null) {
      return;
    }
    stopSpan(request, request.getResponse());
  }

  public static void stopSpan(final Response response) {
    if (response == null) {
      return;
    }
    stopSpan(response.getRequest(), response);
  }

  private static void stopSpan(final Request request, final Response response) {
    if (request == null) {
      return;
    }

    final Object contextObj = request.getAttribute(DD_CONTEXT_ATTRIBUTE);
    if (contextObj instanceof Context) {
      clearContext(request);
      finishSpan((Context) contextObj, response);
    }
  }

  private static void clearContext(final Request request) {
    final com.bes.enterprise.web.crane.Request coyoteRequest = request.getCoyoteRequest();
    if (coyoteRequest != null) {
      coyoteRequest.setAttribute(DD_CONTEXT_ATTRIBUTE, null);
    }
  }
}
