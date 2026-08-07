package datadog.trace.instrumentation.bes;

import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromContext;

import com.bes.enterprise.webtier.connector.Request;
import com.bes.enterprise.webtier.connector.Response;
import datadog.appsec.api.blocking.BlockingContentType;
import datadog.context.Context;
import datadog.trace.api.Config;
import datadog.trace.api.DDTags;
import datadog.trace.api.gateway.BlockResponseFunction;
import datadog.trace.api.internal.TraceSegment;
import datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.URIDataAdapter;
import datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;
import datadog.trace.bootstrap.instrumentation.decorator.HttpServerDecorator;
import jakarta.servlet.ServletException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BesDecorator
    extends HttpServerDecorator<Request, Request, Response, com.bes.enterprise.web.crane.Request> {
  private static final Logger log = LoggerFactory.getLogger(BesDecorator.class);
  public static final CharSequence BES_SERVER = UTF8BytesString.create("bes-server");

  public static final BesDecorator DECORATE = new BesDecorator();
  public static final String DD_PARENT_CONTEXT_ATTRIBUTE = "datadog.parent-context";
  public static final String DD_CONTEXT_PATH_ATTRIBUTE = "datadog.context.path";
  public static final String DD_SERVLET_PATH_ATTRIBUTE = "datadog.servlet.path";
  public static final String DD_REAL_STATUS_CODE = "datadog.servlet.real_status_code";
  public static final CharSequence SERVLET_REQUEST =
      UTF8BytesString.create(DECORATE.operationName());

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"bes"};
  }

  @Override
  protected CharSequence component() {
    return BES_SERVER;
  }

  @Override
  protected AgentPropagation.ContextVisitor<com.bes.enterprise.web.crane.Request> getter() {
    return ExtractAdapter.CraneRequest.GETTER;
  }

  @Override
  protected AgentPropagation.ContextVisitor<Response> responseGetter() {
    return ExtractAdapter.ConnectorResponse.GETTER;
  }

  @Override
  public CharSequence spanName() {
    return SERVLET_REQUEST;
  }

  @Override
  protected String method(final Request request) {
    return request.getMethod();
  }

  @Override
  protected URIDataAdapter url(final Request request) {
    return new RequestURIDataAdapter(request);
  }

  @Override
  protected String peerHostIP(final Request request) {
    return request.getRemoteAddr();
  }

  @Override
  protected int peerPort(final Request request) {
    return request.getRemotePort();
  }

  @Override
  protected String getRequestHeader(final Request request, final String key) {
    return request.getHeader(key);
  }

  @Override
  protected int status(final Response response) {
    int status = response.getStatus();
    if (status == 500) {
      Request request = response.getRequest();
      Integer savedStatus =
          request == null ? null : (Integer) request.getAttribute(DD_REAL_STATUS_CODE);
      if (savedStatus != null) {
        return savedStatus;
      }
    }
    return status;
  }

  @Override
  protected String requestedSessionId(final Request request) {
    return request.getRequestedSessionId();
  }

  @Override
  protected void doOnRequest(
      final AgentSpan span,
      final Request connection,
      final Request request,
      final Context parentContext) {
    if (request != null) {
      String contextPath = request.getContextPath();
      String servletPath = request.getServletPath();

      if (contextPath == null || contextPath.isEmpty()) {
        contextPath = "/";
      }
      span.setTag("servlet.context", contextPath);
      if (servletPath != null && !servletPath.isEmpty()) {
        span.setTag("servlet.path", servletPath);
      }

      request.setAttribute(DD_CONTEXT_PATH_ATTRIBUTE, contextPath);
      request.setAttribute(DD_SERVLET_PATH_ATTRIBUTE, servletPath);
    }
    super.doOnRequest(span, connection, request, parentContext);
  }

  @Override
  protected boolean isAppSecOnResponseSeparate() {
    return true;
  }

  @Override
  protected void doOnResponse(final AgentSpan span, final Response response) {
    if (response == null) {
      log.debug("Skipping BES response decoration because response is null");
      return;
    }
    Request request = response.getRequest();
    if (request != null
        && Config.get().isServletPrincipalEnabled()
        && request.getUserPrincipal() != null) {
      span.setTag(DDTags.USER_NAME, request.getUserPrincipal().getName());
    }
    if (request != null) {
      Object throwable = request.getAttribute("jakarta.servlet.error.exception");
      if (throwable instanceof ServletException) {
        throwable = ((ServletException) throwable).getRootCause();
      }
      if (throwable instanceof Throwable) {
        onError(span, (Throwable) throwable);
      }
    } else {
      log.debug("Skipping BES request-specific response decoration because request is null");
    }
    super.doOnResponse(span, response);
  }

  public static void finishSpan(final Context context, final Response response) {
    if (context == null) {
      return;
    }

    final AgentSpan span = spanFromContext(context);
    try {
      if (span != null) {
        DECORATE.onResponse(span, response);
      }
    } catch (Throwable t) {
      log.debug("Error decorating BES response before span finish", t);
    } finally {
      try {
        DECORATE.beforeFinish(context);
      } catch (Throwable t) {
        log.debug("Error before finishing BES span", t);
      }
      if (span != null) {
        span.finish();
      }
    }
  }

  @Override
  protected BlockResponseFunction createBlockResponseFunction(
      final Request request, final Request connection) {
    return new BesBlockResponseFunction(request);
  }

  public static class BesBlockResponseFunction implements BlockResponseFunction {
    private final Request request;

    public BesBlockResponseFunction(final Request request) {
      this.request = request;
    }

    @Override
    public boolean tryCommitBlockingResponse(
        final TraceSegment segment,
        final int statusCode,
        final BlockingContentType bct,
        final Map<String, String> extraHeaders,
        final String securityResponseId) {
      Response response = request == null ? null : request.getResponse();
      return BesBlockingHelper.commitBlockingResponse(
          segment, request, response, statusCode, bct, extraHeaders, securityResponseId);
    }
  }
}
