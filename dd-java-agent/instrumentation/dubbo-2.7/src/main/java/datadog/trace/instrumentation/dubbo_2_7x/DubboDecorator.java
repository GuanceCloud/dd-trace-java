package datadog.trace.instrumentation.dubbo_2_7x;

import com.alibaba.dubbo.common.Constants;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;
import datadog.trace.bootstrap.instrumentation.decorator.BaseDecorator;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.*;
import static datadog.trace.instrumentation.dubbo_2_7x.DubboHeadersExtractAdapter.GETTER;
import static datadog.trace.instrumentation.dubbo_2_7x.DubboHeadersInjectAdapter.SETTER;
import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER_SIDE;
import static org.apache.dubbo.common.constants.CommonConstants.SIDE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER_SIDE;

public class DubboDecorator extends BaseDecorator {
  private static final Logger log = LoggerFactory.getLogger(DubboDecorator.class);
  public static final CharSequence DUBBO_REQUEST = UTF8BytesString.create("dubbo");

  public static final CharSequence DUBBO_SERVER = UTF8BytesString.create("apache-dubbo");
  public static final CharSequence DUBBO_VERSION = UTF8BytesString.create("2.7");

  public static final DubboDecorator DECORATE = new DubboDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[]{"apache-dubbo.2.7"};
  }

  @Override
  protected CharSequence spanType() {
    return DUBBO_SERVER;
  }

  @Override
  protected CharSequence component() {
    return DUBBO_SERVER;
  }

  public AgentSpan startDubboSpan(Invoker invoker, Invocation invocation) {
    URL url = invoker.getUrl();
    boolean isConsumer = isConsumerSide(url);

    String methodName = invocation.getMethodName();
    String resourceName = generateOperationName(url,invocation);
    String shortUrl = generateRequestURL(url,invocation);

    if (log.isDebugEnabled()) {
      log.debug("isConsumer:{},method:{},resourceName:{},shortUrl:{},longUrl:{},ProtocolServiceKey:{},serviceName:{}",
          isConsumer,
          methodName,
          resourceName,
          shortUrl,
          url.toString(),
          invocation.getProtocolServiceKey(),
          invocation.getServiceName()
          );
    }
    AgentSpan span;

    if (isConsumer){
      // this is consumer
      span = startSpan(DUBBO_REQUEST);
    }else{
      // this is provider
      AgentSpan.Context parentContext = propagate().extract(invocation, GETTER);
      span = startSpan(DUBBO_REQUEST,parentContext);
    }
    span.setTag("url", url.toString());
    span.setTag("short_url", shortUrl);
    span.setTag("method", methodName);
    afterStart(span);

    withMethod(span, resourceName);
    if (isConsumer){
      propagate().inject(span, invocation, SETTER);
//      InstrumentationContext.get(Invocation.class, AgentSpan.class).put(invocation, span);
    }
    return span;
  }

  public void withMethod(final AgentSpan span, final String methodName) {
    span.setResourceName(methodName);
  }

  @Override
  public AgentSpan afterStart(AgentSpan span) {
    span.setTag("dubbo-version",DUBBO_VERSION);
    return super.afterStart(span);
  }


  private String generateOperationName(URL requestURL, Invocation invocation) {
    StringBuilder operationName = new StringBuilder();
    String groupStr = requestURL.getParameter(Constants.GROUP_KEY);
    groupStr = StringUtils.isEmpty(groupStr) ? "" : groupStr + "/";
    operationName.append(groupStr);
    operationName.append(requestURL.getPath());
    operationName.append("." + invocation.getMethodName() + "(");
    for (Class<?> classes : invocation.getParameterTypes()) {
      operationName.append(classes.getSimpleName() + ",");
    }

    if (invocation.getParameterTypes().length > 0) {
      operationName.delete(operationName.length() - 1, operationName.length());
    }
    operationName.append(")");
    return operationName.toString();
  }

  private String generateRequestURL(URL url, Invocation invocation) {
    StringBuilder requestURL = new StringBuilder();
    requestURL.append(url.getProtocol() + "://");
    requestURL.append(url.getHost());
    requestURL.append(":" + url.getPort() + "/");
    requestURL.append(generateOperationName(url, invocation));
    return requestURL.toString();
  }

  public boolean isConsumerSide(URL url) {
    return url.getParameter(SIDE_KEY, PROVIDER_SIDE).equals(CONSUMER_SIDE);
  }

  public AgentScope buildSpan(Invoker invoker, Invocation invocation) {
    RpcContext rpcContext = RpcContext.getServerContext();
    if (null == rpcContext) {
      return null;
    }
    AgentSpan span = startDubboSpan(invoker,invocation);
    span.startThreadMigration();
    AgentScope agentScope = activateSpan(span);
    return agentScope;
  }
}
