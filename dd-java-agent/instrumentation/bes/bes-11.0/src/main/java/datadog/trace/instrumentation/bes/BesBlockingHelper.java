package datadog.trace.instrumentation.bes;

import com.bes.enterprise.webtier.connector.Request;
import com.bes.enterprise.webtier.connector.Response;
import datadog.appsec.api.blocking.BlockingContentType;
import datadog.trace.api.gateway.Flow;
import datadog.trace.api.internal.TraceSegment;
import datadog.trace.bootstrap.blocking.BlockingActionHelper;
import datadog.trace.bootstrap.blocking.BlockingActionHelper.TemplateType;
import datadog.trace.bootstrap.instrumentation.decorator.HttpServerDecorator;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BesBlockingHelper {
  private static final Logger log = LoggerFactory.getLogger(BesBlockingHelper.class);
  private static final MethodHandle GET_OUTPUT_STREAM;

  static {
    MethodHandle methodHandle = null;
    try {
      Method getOutputStream = Response.class.getMethod("getOutputStream");
      methodHandle = MethodHandles.lookup().unreflect(getOutputStream);
    } catch (IllegalAccessException | NoSuchMethodException | RuntimeException e) {
      log.error(
          "Lookup of BES getOutputStream failed. Will be unable to commit blocking response", e);
    }
    GET_OUTPUT_STREAM = methodHandle;
  }

  public static boolean commitBlockingResponse(
      final TraceSegment segment,
      final Request request,
      final Response response,
      final Flow.Action.RequestBlockingAction rba) {
    if (rba == null) {
      log.warn("Cannot commit BES blocking response because blocking action is null");
      return false;
    }
    return commitBlockingResponse(
        segment,
        request,
        response,
        rba.getStatusCode(),
        rba.getBlockingContentType(),
        rba.getExtraHeaders(),
        rba.getSecurityResponseId());
  }

  public static boolean commitBlockingResponse(
      final TraceSegment segment,
      final Request request,
      final Response response,
      final int statusCode,
      final BlockingContentType templateType,
      final Map<String, String> extraHeaders,
      final String securityResponseId) {
    if (request == null) {
      log.warn("Cannot commit BES blocking response because request is null");
      return false;
    }
    if (response == null) {
      log.warn("Cannot commit BES blocking response because response is null");
      return false;
    }
    if (GET_OUTPUT_STREAM == null) {
      log.warn("Cannot commit BES blocking response because getOutputStream lookup failed");
      return false;
    }
    final BlockingContentType blockingContentType =
        templateType == null ? BlockingContentType.AUTO : templateType;
    if (templateType == null) {
      log.warn("BES blocking content type is null. Falling back to AUTO");
    }

    final int httpCode = BlockingActionHelper.getHttpCode(statusCode);
    log.debug(
        "Preparing BES blocking response statusCode={} resolvedStatusCode={} templateType={} extraHeaderCount={}",
        statusCode,
        httpCode,
        blockingContentType,
        extraHeaders == null ? 0 : extraHeaders.size());

    if (!start(request, response, httpCode)) {
      return true;
    }

    request.setAttribute(BesDecorator.DD_REAL_STATUS_CODE, httpCode);

    if (extraHeaders != null) {
      for (Map.Entry<String, String> header : extraHeaders.entrySet()) {
        response.setHeader(header.getKey(), header.getValue());
      }
    }

    try {
      try {
        tryWriteWithOutputStream(request, response, blockingContentType, securityResponseId);
      } catch (IllegalStateException ise) {
        log.debug("BES output stream is unavailable. Trying writer for blocking response", ise);
        tryWriteWithWriter(request, response, blockingContentType, securityResponseId);
      }
      markEffectivelyBlocked(segment);
      log.debug("BES blocking response committed");
    } catch (Throwable e) {
      log.warn("Error committing BES blocking response", e);
    }
    return true;
  }

  private static void tryWriteWithOutputStream(
      final Request request,
      final Response response,
      final BlockingContentType templateType,
      final String securityResponseId)
      throws Throwable {
    OutputStream outputStream = (OutputStream) GET_OUTPUT_STREAM.invoke(response);
    if (outputStream == null) {
      throw new IOException("BES response returned null output stream");
    }
    try {
      if (templateType != BlockingContentType.NONE) {
        TemplateType type =
            BlockingActionHelper.determineTemplateType(templateType, request.getHeader("Accept"));
        byte[] template = BlockingActionHelper.getTemplate(type, securityResponseId);

        response.setHeader("Content-length", Integer.toString(template.length));
        response.setHeader("Content-type", BlockingActionHelper.getContentType(type));
        log.debug("Writing BES blocking response using output stream, templateType={}", type);
        outputStream.write(template);
      } else {
        log.debug("Writing BES blocking response with empty body");
      }
    } finally {
      outputStream.close();
    }
  }

  private static void tryWriteWithWriter(
      final Request request,
      final Response response,
      final BlockingContentType templateType,
      final String securityResponseId)
      throws IOException {
    PrintWriter writer = response.getWriter();
    if (writer == null) {
      throw new IOException("BES response returned null writer");
    }
    try {
      if (templateType != BlockingContentType.NONE) {
        TemplateType type =
            BlockingActionHelper.determineTemplateType(templateType, request.getHeader("Accept"));
        byte[] template = BlockingActionHelper.getTemplate(type, securityResponseId);
        String templateString = new String(template, StandardCharsets.UTF_8);

        if ("utf-8".equalsIgnoreCase(response.getCharacterEncoding())) {
          response.setHeader("Content-length", Integer.toString(template.length));
        }
        response.setHeader("Content-type", BlockingActionHelper.getContentType(type));
        log.debug("Writing BES blocking response using writer, templateType={}", type);
        writer.write(templateString);
      } else {
        log.debug("Writing BES blocking response with empty body");
      }
    } finally {
      writer.close();
    }
  }

  private static void markEffectivelyBlocked(final TraceSegment segment) {
    if (segment == null) {
      log.debug("BES blocking response committed without trace segment");
      return;
    }
    try {
      segment.effectivelyBlocked();
    } catch (Throwable t) {
      log.debug("Could not mark BES trace segment as effectively blocked", t);
    }
  }

  private static boolean start(
      final Request request, final Response response, final int statusCode) {
    try {
      if (response.isCommitted()) {
        log.warn("BES response already committed; cannot change it to a blocking response");
        return false;
      }

      log.debug("Starting BES blocking response commit");

      response.reset();
      request.setAttribute(HttpServerDecorator.DD_IGNORE_COMMIT_ATTRIBUTE, Boolean.TRUE);
      response.setStatus(statusCode);
    } catch (RuntimeException e) {
      log.warn("Failed to prepare BES response for blocking", e);
      return false;
    }

    return true;
  }
}
