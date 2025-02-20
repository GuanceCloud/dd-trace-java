package datadog.trace.instrumentation.pekkohttp.iast.helpers;

import datadog.trace.api.iast.InstrumentationBridge;
import datadog.trace.api.iast.SourceTypes;
import datadog.trace.api.iast.propagation.PropagationModule;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.pekko.http.scaladsl.model.HttpRequest;
import scala.Tuple1;
import scala.compat.java8.JFunction1;

public class TaintRequestFunction implements JFunction1<Tuple1<HttpRequest>, Tuple1<HttpRequest>> {
  public static final TaintRequestFunction INSTANCE = new TaintRequestFunction();

  @Override
  @SuppressFBWarnings("BC_IMPOSSIBLE_INSTANCEOF")
  public Tuple1<HttpRequest> apply(Tuple1<HttpRequest> v1) {
    HttpRequest httpRequest = v1._1();

    PropagationModule mod = InstrumentationBridge.PROPAGATION;
    if (mod == null) {
      return v1;
    }
    mod.taint(httpRequest, SourceTypes.REQUEST_BODY);

    return v1;
  }
}
