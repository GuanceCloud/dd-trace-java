package datadog.trace.instrumentation.rocketmq5;

import static datadog.context.propagation.Propagators.defaultPropagator;
import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.*;
import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;
import static datadog.trace.instrumentation.rocketmq5.MessageMapSetter.SETTER;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.context.ContextScope;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.java.message.MessageBuilderImpl;

@AutoService(InstrumenterModule.class)
public class MessageImplInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForTypeHierarchy, Instrumenter.HasMethodAdvice {
  public static String CLASS_NAME = "";

  public MessageImplInstrumentation() {
    super("rocketmq-5.0");
  }

  @Override
  public String hierarchyMarkerType() {
    return "org.apache.rocketmq.client.java.message.MessageBuilderImpl";
  }

  @Override
  public ElementMatcher<TypeDescription> hierarchyMatcher() {
    return named(hierarchyMarkerType());
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".ReceiveSpanFinishingCallback",
      packageName + ".MessageListenerWrapper",
      packageName + ".MessageMapSetter",
      packageName + ".MessageViewGetter",
      packageName + ".SendSpanFinishingCallback",
    };
  }

  @Override
  public void methodAdvice(MethodTransformer transformation) {
    transformation.applyAdvice(
        isMethod().and(named("build")).and(takesArguments(0)),
        MessageImplInstrumentation.class.getName() + "$BuildAdvice");
  }

  public static class BuildAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ContextScope onEnter(@Advice.This MessageBuilderImpl impl) {
      AgentSpan span = startSpan("rocketmq", "message build send");
      span.setSpanType("rocketmq");
      ContextScope scope = activateSpan(span);
      defaultPropagator().inject(span, impl, SETTER);
      return scope;
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.Enter ContextScope scope, @Advice.Return Message message) {
      spanFromScope(scope).setServiceName("rocketmq-producer");
      spanFromScope(scope).setTag("topic", message.getTopic());
      spanFromScope(scope).setTag("tag", message.getTag());
      spanFromScope(scope).setTag("messageGroup", message.getMessageGroup());
      spanFromScope(scope).setTag("keys", message.getKeys());
      scope.close();
      spanFromScope(scope).finish();
    }
  }
}
