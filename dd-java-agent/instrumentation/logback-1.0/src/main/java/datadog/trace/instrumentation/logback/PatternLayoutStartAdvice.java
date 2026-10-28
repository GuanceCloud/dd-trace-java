package datadog.trace.instrumentation.logback;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.core.pattern.PatternLayoutBase;
import ch.qos.logback.core.pattern.parser.CompositeNode;
import ch.qos.logback.core.pattern.parser.Node;
import ch.qos.logback.core.pattern.parser.Parser;
import ch.qos.logback.core.pattern.parser.SimpleKeywordNode;
import datadog.trace.api.Config;
import java.util.ArrayDeque;
import java.util.List;
import net.bytebuddy.asm.Advice;

/** Adds missing correlation converters while preserving the application's pattern. */
public class PatternLayoutStartAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(@Advice.This PatternLayoutBase<?> layout) {
    if (!Config.get().isLogPatternInject() || !(layout instanceof PatternLayout)) {
      return;
    }
    String pattern = layout.getPattern();
    if (pattern == null || pattern.isEmpty()) {
      return;
    }

    boolean missingTraceId = true;
    boolean missingSpanId = true;
    ArrayDeque<Node> pending = new ArrayDeque<>();
    Node root;
    try {
      root = new Parser<>(pattern).parse();
    } catch (Exception invalidPattern) {
      // Let Logback report malformed patterns through its normal validation.
      return;
    }
    if (root != null) {
      pending.push(root);
    }
    // Keep traversal inline so bootstrap-loaded layouts do not need an injected helper.
    scan:
    while (!pending.isEmpty()) {
      for (Node node = pending.pop(); node != null; node = node.getNext()) {
        if (node instanceof CompositeNode) {
          Node child = ((CompositeNode) node).getChildNode();
          if (child != null) {
            pending.push(child);
          }
        } else if (node instanceof SimpleKeywordNode
            && ("X".equals(node.getValue()) || "mdc".equals(node.getValue()))) {
          List<String> options = ((SimpleKeywordNode) node).getOptions();
          if (options == null || options.isEmpty()) {
            missingTraceId = false;
            missingSpanId = false;
            break scan;
          }
          String key = options.get(0);
          int defaultSeparator = key.indexOf(":-");
          if (defaultSeparator >= 0) {
            key = key.substring(0, defaultSeparator);
          }
          if ("dd.trace_id".equals(key)) {
            missingTraceId = false;
          } else if ("dd.span_id".equals(key)) {
            missingSpanId = false;
          }
          if (!missingTraceId && !missingSpanId) {
            break scan;
          }
        }
      }
    }
    if (missingTraceId || missingSpanId) {
      StringBuilder result = new StringBuilder();
      if (missingTraceId) {
        result.append("dd.trace_id=%X{dd.trace_id} ");
      }
      if (missingSpanId) {
        result.append("dd.span_id=%X{dd.span_id} ");
      }
      layout.setPattern(result.append(pattern).toString());
    }
  }
}
