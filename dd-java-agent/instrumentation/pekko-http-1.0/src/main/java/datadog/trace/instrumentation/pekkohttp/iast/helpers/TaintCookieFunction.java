package datadog.trace.instrumentation.pekkohttp.iast.helpers;

import datadog.trace.api.iast.InstrumentationBridge;
import datadog.trace.api.iast.SourceTypes;
import datadog.trace.api.iast.propagation.PropagationModule;
import org.apache.pekko.http.scaladsl.model.headers.HttpCookiePair;
import scala.Tuple1;
import scala.compat.java8.JFunction1;

public class TaintCookieFunction
    implements JFunction1<Tuple1<HttpCookiePair>, Tuple1<HttpCookiePair>> {
  public static final TaintCookieFunction INSTANCE = new TaintCookieFunction();

  @Override
  public Tuple1<HttpCookiePair> apply(Tuple1<HttpCookiePair> v1) {
    HttpCookiePair httpCookiePair = v1._1();

    PropagationModule mod = InstrumentationBridge.PROPAGATION;
    if (mod == null || httpCookiePair == null) {
      return v1;
    }
    final String name = httpCookiePair.name();
    final String value = httpCookiePair.value();
    mod.taint(name, SourceTypes.REQUEST_COOKIE_NAME, name);
    mod.taint(value, SourceTypes.REQUEST_COOKIE_VALUE, name);
    return v1;
  }
}
