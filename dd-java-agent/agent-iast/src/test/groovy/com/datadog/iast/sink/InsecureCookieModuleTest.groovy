package com.datadog.iast.sink

import com.datadog.iast.IastModuleImplTestBase
import com.datadog.iast.IastRequestContext
import com.datadog.iast.model.Vulnerability
import com.datadog.iast.model.VulnerabilityType
import datadog.trace.api.gateway.RequestContext
import datadog.trace.api.gateway.RequestContextSlot
import datadog.trace.api.iast.InstrumentationBridge
import datadog.trace.api.iast.util.Cookie
import datadog.trace.bootstrap.instrumentation.api.AgentSpan
import groovy.transform.CompileDynamic

@CompileDynamic
class InsecureCookieModuleTest extends IastModuleImplTestBase {

  private List<Object> objectHolder

  private IastRequestContext ctx

  private HttpResponseHeaderModuleImpl module

  private AgentSpan span

  def setup() {
    InstrumentationBridge.clearIastModules()
    module = new HttpResponseHeaderModuleImpl(dependencies)
    InstrumentationBridge.registerIastModule(new InsecureCookieModuleImpl())
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

  void 'report insecure cookie with InsecureCookieModule.onCookie'() {
    given:
    Vulnerability savedVul
    final cookie =  Cookie.named('user-id').build()

    when:
    module.onCookie(cookie)

    then:
    1 * tracer.activeSpan() >> span
    1 * overheadController.consumeQuota(_, _) >> true
    1 * reporter.report(_, _ as Vulnerability) >> { savedVul = it[1] }
    with(savedVul) {
      type == VulnerabilityType.INSECURE_COOKIE
      location != null
      with(evidence) {
        value == cookie.cookieName
      }
    }
  }

  void 'cases where nothing is reported during InsecureCookieModuleTest.onCookie'() {
    given:
    final cookie = Cookie.named('user-id')
      .secure(true)
      .httpOnly(true)
      .sameSite('Strict')
      .build()

    when:
    module.onCookie(cookie)

    then:
    0 * tracer.activeSpan()
    0 * overheadController._
    0 * reporter._
  }

  void 'secure cookie is not reported with InsecureCookieModule.onCookie'() {
    given:
    final cookie = Cookie.named('user-id').secure(true).build()

    when:
    module.onCookie(cookie)

    then:
    0 * tracer.activeSpan() >> span
    0 * overheadController.consumeQuota(_, _) >> true
    0 * reporter.report(_, _ as Vulnerability)
  }
}
