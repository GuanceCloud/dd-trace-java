package datadog.trace.instrumentation.rocketmq5;

import static datadog.trace.bootstrap.instrumentation.api.AgentPropagation.extractContextAndGetSpanContext;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.*;
import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;
import static datadog.trace.instrumentation.rocketmq5.MessageViewGetter.GetterView;

import apache.rocketmq.v2.ReceiveMessageRequest;
import datadog.context.ContextScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.AgentSpanContext;
import java.util.List;
import org.apache.rocketmq.client.java.impl.consumer.ReceiveMessageResult;
import org.apache.rocketmq.client.java.message.MessageViewImpl;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.FutureCallback;

public class ReceiveSpanFinishingCallback implements FutureCallback<ReceiveMessageResult> {

  private final ReceiveMessageRequest request;
  private final Timer timer;

  public ReceiveSpanFinishingCallback(ReceiveMessageRequest request, Timer timer) {
    this.request = request;
    this.timer = timer;
  }

  @Override
  public void onSuccess(ReceiveMessageResult receiveMessageResult) {
    List<MessageViewImpl> messageViews = receiveMessageResult.getMessageViewImpls();
    // Don't create spans when no messages were received.
    if (messageViews.isEmpty()) {
      return;
    }
    String consumerGroup = request.getGroup().getName();
    String topic = request.getMessageQueue().getTopic().getName();

    for (MessageViewImpl messageView : messageViews) {
      //  propagate().inject(span.context(),messageView,setterView);
      AgentSpanContext parentContext = extractContextAndGetSpanContext(messageView, GetterView);
      AgentSpan childSpan;
      if (null != parentContext) {
        childSpan = startSpan("rocketmq", "receive_message", parentContext);
      } else {
        childSpan = startSpan("rocketmq", "receive_message");
      }
      childSpan.setServiceName("rocketmq-consume");
      childSpan.setTag("messageID", messageView.getMessageId());
      ContextScope scopeC = activateSpan(childSpan);
      spanFromScope(scopeC).setSpanType("rocketmq");
      spanFromScope(scopeC).setTag("groupID", consumerGroup);
      spanFromScope(scopeC).setTag("topic", topic);
      spanFromScope(scopeC).setTag("status", "success");

      scopeC.close();
      spanFromScope(scopeC).finish();
    }
  }

  @Override
  public void onFailure(Throwable throwable) {}
}
