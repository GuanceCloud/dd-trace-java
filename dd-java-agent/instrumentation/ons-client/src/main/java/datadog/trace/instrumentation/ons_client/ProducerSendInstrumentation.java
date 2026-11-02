package datadog.trace.instrumentation.ons_client;

import static datadog.trace.agent.tooling.bytebuddy.matcher.HierarchyMatchers.implementsInterface;
import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;
import static datadog.trace.instrumentation.ons_client.ProducerDecorator.PRODUCER_DECORATOR;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.SendCallback;
import com.google.auto.service.AutoService;
import datadog.context.ContextScope;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumenterModule.class)
public class ProducerSendInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForTypeHierarchy, Instrumenter.HasMethodAdvice {

  public static final String CLASS_NAME = "com.aliyun.openservices.ons.api.Producer";

  public ProducerSendInstrumentation() {
    super("rocketmq", "ons-client");
  }

  @Override
  public String hierarchyMarkerType() {
    return CLASS_NAME;
  }

  @Override
  public ElementMatcher<TypeDescription> hierarchyMatcher() {
    return implementsInterface(named(hierarchyMarkerType()));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".ExtractAdapter",
      packageName + ".InjectAdapter",
      packageName + ".WrappingSendCallback",
      packageName + ".ProducerDecorator",
    };
  }

  @Override
  public void methodAdvice(MethodTransformer transformation) {
    transformation.applyAdvice(
        isMethod().and(named("send")).and(takesArguments(1)),
        ProducerSendInstrumentation.class.getName() + "$SendAdvice");
    transformation.applyAdvice(
        isMethod()
            .and(named("sendOneway"))
            . // 没有返回值
            and(takesArguments(1)),
        ProducerSendInstrumentation.class.getName() + "$SendOnewayAdvice");
    transformation.applyAdvice(
        isMethod()
            .and(named("sendAsync"))
            . // 没有返回值
            and(takesArguments(2)),
        ProducerSendInstrumentation.class.getName() + "$sendAsyncAdvice");
  }

  public static class SendAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ContextScope onEnter(@Advice.Argument(0) Message message) {
      // 有返回值
      return PRODUCER_DECORATOR.OnStart(message);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Enter final ContextScope scope, @Advice.Thrown final Throwable throwable) {
      if (scope == null) {
        return;
      }
      PRODUCER_DECORATOR.OnEnd(scope, throwable);
    }
  }

  public static class SendOnewayAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ContextScope onEnter(@Advice.Argument(0) Message message) {
      return PRODUCER_DECORATOR.OnStart(message);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Enter final ContextScope scope, @Advice.Thrown final Throwable throwable) {
      if (scope == null) {
        return;
      }
      PRODUCER_DECORATOR.OnEnd(scope, throwable);
    }
  }

  public static class sendAsyncAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ContextScope onEnter(
        @Advice.Argument(0) Message message,
        @Advice.Argument(value = 1, readOnly = false) SendCallback callback) {
      if (callback == null) {
        return null;
      }
      ContextScope scope = PRODUCER_DECORATOR.OnStart(message);
      callback = new WrappingSendCallback(callback, spanFromScope(scope));
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Enter ContextScope scope,
        @Advice.Argument(1) SendCallback callback,
        @Advice.Thrown Throwable throwable) {
      if (scope == null) {
        return;
      }
      try {
        if (throwable != null) {
          ((WrappingSendCallback) callback).onSendError(throwable);
        }
      } finally {
        scope.close();
        ((WrappingSendCallback) callback).onSendExit();
      }
    }
  }
}
