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
    boolean missingService = true;
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
            missingService = false;
            break scan;
          }
          String key = options.get(0);
          int defaultSeparator = key.indexOf(":-");
          if (defaultSeparator >= 0) {
            key = key.substring(0, defaultSeparator);
          }
          if ("dd.trace_id".equals(key)) {
            missingTraceId = false;
          } else if ("dd.service".equals(key)) {
            missingService = false;
          }
          if (!missingTraceId && !missingService) {
            break scan;
          }
        }
      }
    }
    if (missingTraceId || missingService) {
      // Preserve the first complete field, including date options and presentation wrappers.
      int insertion = pattern.length();
      int optionsDepth = 0;
      int compositeDepth = 0;
      int bracketDepth = 0;
      char quote = 0;
      boolean fieldStarted = false;
      for (int i = 0; i < pattern.length(); i++) {
        char c = pattern.charAt(i);
        if (c == '\\') {
          fieldStarted = true;
          i++;
          continue;
        }
        if (optionsDepth > 0) {
          if (quote != 0) {
            if (c == quote) {
              quote = 0;
            }
          } else if (c == '\'' || c == '"') {
            quote = c;
          } else if (c == '{') {
            optionsDepth++;
          } else if (c == '}') {
            optionsDepth--;
          }
          continue;
        }
        if (c == '{') {
          optionsDepth++;
        } else if (c == '(') {
          compositeDepth++;
        } else if (c == ')' && compositeDepth > 0) {
          compositeDepth--;
        } else if (compositeDepth == 0 && c == '[') {
          bracketDepth++;
        } else if (compositeDepth == 0 && c == ']' && bracketDepth > 0) {
          bracketDepth--;
        }
        if (compositeDepth == 0 && fieldStarted) {
          if ((c == '%' && i + 1 < pattern.length() && pattern.charAt(i + 1) == 'n')
              || c == '\n'
              || c == '\r') {
            insertion = i;
            break;
          }
          if (bracketDepth == 0
              && (Character.isWhitespace(c) || c == '|' || c == ',' || c == ';')) {
            insertion = i;
            // Keep the original separator before the new fields, including delimited logs.
            while (insertion < pattern.length()
                && Character.isWhitespace(pattern.charAt(insertion))
                && pattern.charAt(insertion) != '\n'
                && pattern.charAt(insertion) != '\r') {
              insertion++;
            }
            if (insertion < pattern.length()
                && (pattern.charAt(insertion) == '|'
                    || pattern.charAt(insertion) == ','
                    || pattern.charAt(insertion) == ';')) {
              insertion++;
              while (insertion < pattern.length()
                  && Character.isWhitespace(pattern.charAt(insertion))
                  && pattern.charAt(insertion) != '\n'
                  && pattern.charAt(insertion) != '\r') {
                insertion++;
              }
            }
            break;
          }
        }
        fieldStarted |= !Character.isWhitespace(c);
      }
      StringBuilder result = new StringBuilder();
      result.append(pattern, 0, insertion);
      if (insertion > 0) {
        char previous = pattern.charAt(insertion - 1);
        if (!Character.isWhitespace(previous)
            && previous != '|'
            && previous != ','
            && previous != ';') {
          result.append(' ');
        }
      }
      if (missingService) {
        result.append("dd.service=%X{dd.service} ");
      }
      if (missingTraceId) {
        result.append("dd.trace_id=%X{dd.trace_id} ");
      }
      if (insertion == pattern.length()
          || pattern.startsWith("%n", insertion)
          || pattern.charAt(insertion) == '\n'
          || pattern.charAt(insertion) == '\r') {
        result.setLength(result.length() - 1);
      }
      layout.setPattern(result.append(pattern, insertion, pattern.length()).toString());
    }
  }
}
