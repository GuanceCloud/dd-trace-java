package datadog.trace.instrumentation.thrift;

import static datadog.trace.agent.tooling.bytebuddy.matcher.HierarchyMatchers.extendsClass;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;
import static datadog.trace.instrumentation.thrift.ThriftClientDecorator.CLIENT_DECORATOR;
import static datadog.trace.instrumentation.thrift.ThriftConstants.CLIENT_INJECT_THREAD;
import static datadog.trace.instrumentation.thrift.ThriftConstants.INSTRUMENTATION_NAME;
import static datadog.trace.instrumentation.thrift.ThriftConstants.INSTRUMENTATION_NAME_CLIENT;
import static datadog.trace.instrumentation.thrift.ThriftConstants.TSERVICE_CLIENT;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.context.ContextScope;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.TBase;
import org.apache.thrift.TServiceClient;

@AutoService(InstrumenterModule.class)
public class TServiceClientInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForTypeHierarchy, Instrumenter.HasMethodAdvice {
  public TServiceClientInstrumentation() {
    super(INSTRUMENTATION_NAME, INSTRUMENTATION_NAME_CLIENT);
  }

  @Override
  public String hierarchyMarkerType() {
    return TSERVICE_CLIENT;
  }

  @Override
  public ElementMatcher<TypeDescription> hierarchyMatcher() {
    return extendsClass(named(hierarchyMarkerType()));
  }

  @Override
  public void methodAdvice(MethodTransformer transformation) {
    transformation.applyAdvice(
        isConstructor().and(takesArgument(1, named("org.apache.thrift.protocol.TProtocol"))),
        packageName + ".TClientConstructorAdvice");

    transformation.applyAdvice(
        isMethod()
            .and(isPrivate())
            .and(named("sendBase"))
            .and(takesArguments(3))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, named("org.apache.thrift.TBase"))),
        getClass().getName() + "$SendBaseAdvice");

    transformation.applyAdvice(
        isMethod()
            .and(isProtected())
            //            .and(takesArgument(1,String.class))
            .and(named("receiveBase")),
        getClass().getName() + "$ReceiveBaseAdvice");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".ThriftConstants",
      packageName + ".ThriftBaseDecorator",
      packageName + ".ThriftClientDecorator",
      packageName + ".ThriftConstants$Tags",
      packageName + ".AbstractContext",
      packageName + ".ClientOutProtocolWrapper",
      packageName + ".ServerInProtocolWrapper",
      packageName + ".TClientConstructorAdvice",
      packageName + ".InjectAdepter",
      packageName + ".Context"
    };
  }

  @Override
  public Map<String, String> contextStore() {
    return Collections.singletonMap("org.apache.thrift.TServiceClient", AgentSpan.class.getName());
  }

  public static class SendBaseAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ContextScope onEnter(
        @Advice.Argument(0) String methodName, @Advice.Argument(1) TBase tb) {
      return activateSpan(CLIENT_DECORATOR.createSpan(methodName, tb));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.This TServiceClient client,
        @Advice.Enter ContextScope scope,
        @Advice.Argument(2) byte messageType,
        @Advice.Thrown Throwable throwable) {
      if (scope == null) {
        return;
      }
      AgentSpan span = spanFromScope(scope);
      boolean finish =
          throwable != null || messageType == org.apache.thrift.protocol.TMessageType.ONEWAY;
      try {
        if (finish) {
          CLIENT_DECORATOR.onError(span, throwable);
          CLIENT_DECORATOR.beforeFinish(span);
        } else {
          InstrumentationContext.get(TServiceClient.class, AgentSpan.class).put(client, span);
        }
      } finally {
        CLIENT_INJECT_THREAD.remove();
        scope.close();
        if (finish) {
          span.finish();
        }
      }
    }
  }

  public static class ReceiveBaseAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ContextScope onEnter(@Advice.This TServiceClient client) {
      AgentSpan span =
          InstrumentationContext.get(TServiceClient.class, AgentSpan.class).remove(client);
      return span == null ? null : activateSpan(span);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter ContextScope scope, @Advice.Thrown Throwable throwable) {
      if (scope == null) {
        return;
      }
      AgentSpan span = spanFromScope(scope);
      try {
        CLIENT_DECORATOR.onError(span, throwable);
        CLIENT_DECORATOR.beforeFinish(span);
      } finally {
        scope.close();
        span.finish();
      }
    }
  }
}
