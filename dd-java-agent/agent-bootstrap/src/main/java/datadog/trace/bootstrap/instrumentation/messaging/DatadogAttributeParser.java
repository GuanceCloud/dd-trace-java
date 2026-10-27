package datadog.trace.bootstrap.instrumentation.messaging;

import static java.nio.charset.StandardCharsets.UTF_8;

import datadog.trace.api.Config;
import datadog.trace.api.DDTraceId;
import datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import java.nio.ByteBuffer;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Parses trace context from an embedded '_datadog' message attribute. */
public final class DatadogAttributeParser {
  private static final Logger log = LoggerFactory.getLogger(DatadogAttributeParser.class);

  private static final Base64.Decoder BASE_64 = Base64.getDecoder();

  /** Parses trace context properties from the given JSON and passes them to the classifier. */
  public static void forEachProperty(AgentPropagation.KeyClassifier classifier, String json) {
    if (null == json) {
      return;
    }
    try {
      if (acceptTraceIdProperty(classifier, json)) {
        acceptJsonProperty(classifier, json, "x-datadog-parent-id");
        acceptJsonProperty(classifier, json, "x-datadog-sampling-priority");
        acceptJsonProperty(classifier, json, "x-datadog-tags");
      }
      if (Config.get().isDataStreamsEnabled()) {
        acceptJsonProperty(classifier, json, "dd-pathway-ctx-base64");
      }
    } catch (Exception e) {
      log.debug("Problem extracting _datadog context", e);
    }
  }

  /** Parses trace context properties from the given JSON and passes them to the classifier. */
  public static void forEachProperty(AgentPropagation.KeyClassifier classifier, ByteBuffer json) {
    if (null == json) {
      return;
    }
    try {
      String jsonStr;
      // peak at the first character to know if we're dealing with json or base64 (since '{' is not
      // a valid b64 char).
      if (json.get(0) == '{') {
        jsonStr = UTF_8.decode(json).toString();
      } else {
        // TODO remove this branch once we are sure that this is never happening.
        // passing a base64 string in a byte buffer is a nonsense, since the base64 is a less
        // efficient representation.
        jsonStr = UTF_8.decode(BASE_64.decode(json)).toString();
      }
      forEachProperty(classifier, jsonStr);
    } catch (Exception e) {
      log.debug("Problem decoding _datadog context", e);
    }
  }

  private static boolean acceptJsonProperty(
      AgentPropagation.KeyClassifier classifier, String json, String key) {
    String value = jsonPropertyValue(json, key);
    return null != value && classifier.accept(key, value);
  }

  // Simple parser that assumes values are JSON strings that don't contain escaped quotes
  private static String jsonPropertyValue(String json, String key) {
    int keyStart = indexOfPropertyName(json, key);
    if (keyStart > 0) {
      int separator = json.indexOf(':', keyStart + key.length());
      if (separator > 0) {
        int valueStart = json.indexOf('"', separator + 1);
        if (valueStart > 0) {
          int valueEnd = json.indexOf('"', valueStart + 1);
          if (valueEnd > 0) {
            return json.substring(valueStart + 1, valueEnd);
          }
        }
      }
    }
    return null;
  }

  /**
   * Finds where {@code key} is used as a property name, that is quoted on both sides, skipping any
   * copy of the same text that sits inside a value. The {@code x-datadog-tags} header now carries
   * application-supplied values, so a value can contain whatever the application named its ML app
   * or session, including the name of another property.
   */
  private static int indexOfPropertyName(String json, String key) {
    int from = 0;
    while (true) {
      int at = json.indexOf(key, from);
      if (at < 1) {
        return -1;
      }
      int after = at + key.length();
      if ('"' == json.charAt(at - 1) && after < json.length() && '"' == json.charAt(after)) {
        return at;
      }
      from = after;
    }
  }

  private static boolean acceptTraceIdProperty(
      AgentPropagation.KeyClassifier classifier, String json) {
    String traceId = jsonPropertyValue(json, "x-datadog-trace-id");
    return traceId != null
        && classifier.accept("x-datadog-trace-id", normalizeTraceIdForDatadogExtraction(traceId));
  }

  private static String normalizeTraceIdForDatadogExtraction(String traceId) {
    try {
      DDTraceId parsed = DDTraceId.from(traceId);
      return Long.toUnsignedString(parsed.toLong());
    } catch (NumberFormatException ignored) {
    }
    try {
      DDTraceId parsed = DDTraceId.fromHex(traceId);
      return Long.toUnsignedString(parsed.toLong());
    } catch (NumberFormatException ignored) {
    }
    return traceId;
  }
}
