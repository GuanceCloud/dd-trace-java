package com.datadog.iast.sink

import com.datadog.iast.IastModuleImplTestBase
import com.datadog.iast.IastRequestContext
import com.datadog.iast.RequestEndedHandler
import com.datadog.iast.model.Vulnerability
import com.datadog.iast.model.VulnerabilityType
import datadog.trace.api.gateway.Flow
import datadog.trace.api.gateway.IGSpanInfo
import datadog.trace.api.gateway.RequestContext
import datadog.trace.api.gateway.RequestContextSlot
import datadog.trace.api.iast.InstrumentationBridge
import datadog.trace.api.internal.TraceSegment
import datadog.trace.bootstrap.instrumentation.api.AgentSpan

public class XContentTypeOptionsModuleTest extends IastModuleImplTestBase {

  private List<Object> objectHolder

  private IastRequestContext ctx

  private XContentTypeModuleImpl module

  private AgentSpan span

  def setup() {
    InstrumentationBridge.clearIastModules()
    module = new XContentTypeModuleImpl(dependencies)
    InstrumentationBridge.registerIastModule(module)
    objectHolder = []
    ctx = new IastRequestContext()
    final reqCtx = Mock(RequestContext) {
      getData(RequestContextSlot.IAST) >> ctx
    }
    span = Mock(AgentSpan) {
      getSpanId() >> 123456
      getRequestContext() >> reqCtx
    }
  }


  void 'x content options sniffing vulnerability'() {
    given:
    Vulnerability savedVul1
    final iastCtx = Mock(IastRequestContext)
    iastCtx.getContentType() >> "text/html"
    final handler = new RequestEndedHandler(dependencies)
    final TraceSegment traceSegment = Mock(TraceSegment)
    final reqCtx = Mock(RequestContext)
    reqCtx.getTraceSegment() >> traceSegment
    reqCtx.getData(RequestContextSlot.IAST) >> iastCtx
    final tags = Mock(Map<String, Object>)
    tags.get("http.status_code") >> 200i
    final spanInfo = Mock(IGSpanInfo)
    spanInfo.getTags() >> tags
    IastRequestContext.get(span) >> iastCtx


    when:
    def flow = handler.apply(reqCtx, spanInfo)

    then:
    flow.getAction() == Flow.Action.Noop.INSTANCE
    flow.getResult() == null
    1 * reqCtx.getData(RequestContextSlot.IAST) >> iastCtx
    1 * reqCtx.getTraceSegment() >> traceSegment
    1 * traceSegment.setTagTop("_dd.iast.enabled", 1)
    1 * iastCtx.getTaintedObjects() >> null
    1 * overheadController.releaseRequest()
    1 * spanInfo.getTags() >> tags
    1 * tags.get('http.status_code') >> 200i
    1 * iastCtx.getxContentTypeOptions() >> null
    1 * tracer.activeSpan() >> span
    1 * iastCtx.getContentType() >> "text/html"
    1 * reporter.report(_, _ as Vulnerability) >> {
      savedVul1 = it[1]
    }

    with(savedVul1) {
      type == VulnerabilityType.XCONTENTTYPE_HEADER_MISSING
    }
  }


  void 'no x content options sniffing reported'() {
    given:
    final iastCtx = Mock(IastRequestContext)
    iastCtx.getxForwardedProto() >> 'https'
    iastCtx.getContentType() >> "text/html"
    final handler = new RequestEndedHandler(dependencies)
    final TraceSegment traceSegment = Mock(TraceSegment)
    final reqCtx = Mock(RequestContext)
    reqCtx.getTraceSegment() >> traceSegment
    reqCtx.getData(RequestContextSlot.IAST) >> iastCtx
    final tags = Mock(Map<String, Object>)
    tags.get("http.url") >> url
    tags.get("http.status_code") >> status
    final spanInfo = Mock(IGSpanInfo)
    spanInfo.getTags() >> tags
    IastRequestContext.get(span) >> iastCtx


    when:
    def flow = handler.apply(reqCtx, spanInfo)

    then:
    flow.getAction() == Flow.Action.Noop.INSTANCE
    flow.getResult() == null
    1 * reqCtx.getData(RequestContextSlot.IAST) >> iastCtx
    1 * reqCtx.getTraceSegment() >> traceSegment
    1 * traceSegment.setTagTop("_dd.iast.enabled", 1)
    1 * iastCtx.getTaintedObjects() >> null
    1 * overheadController.releaseRequest()
    1 * spanInfo.getTags() >> tags
    1 * iastCtx.getContentType() >> "text/html"
    1 * tags.get('http.status_code') >> status
    1 * iastCtx.getxContentTypeOptions()
    0 * _

    where:
    url                   | status
    "https://localhost/a" | 307i
    "https://localhost/a" | HttpURLConnection.HTTP_MOVED_PERM
    "https://localhost/a" | HttpURLConnection.HTTP_MOVED_TEMP
    "https://localhost/a" | HttpURLConnection.HTTP_NOT_MODIFIED
    "https://localhost/a" | HttpURLConnection.HTTP_NOT_FOUND
    "https://localhost/a" | HttpURLConnection.HTTP_GONE
    "https://localhost/a" | HttpURLConnection.HTTP_INTERNAL_ERROR
  }



  void 'throw exception if context is null'(){
    when:
    module.onRequestEnd(null, null)

    then:
    noExceptionThrown()
  }
}
