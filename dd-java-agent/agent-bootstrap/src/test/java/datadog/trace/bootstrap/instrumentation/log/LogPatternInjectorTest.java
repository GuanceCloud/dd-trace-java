package datadog.trace.bootstrap.instrumentation.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.tabletest.junit.TableTest;

class LogPatternInjectorTest {
  private static final String SERVICE = "dd.service=%X{dd.service} ";
  private static final String TRACE = "dd.trace_id=%X{dd.trace_id} ";
  private static final String SPAN = "dd.span_id=%X{dd.span_id} ";

  @TableTest({
    "Scenario       | Template                                      | Service | Trace | Span ",
    "plain          | %d{yyyy-MM-dd HH:mm:ss} @%m%n                 | true    | true  | true ",
    "brackets       | '[%d{yyyy-MM-dd HH:mm:ss}] @%m%n'             | true    | true  | true ",
    "service        | %X{dd.service} @%m%n                          | false   | true  | true ",
    "trace          | %X{dd.trace_id} @%m%n                         | true    | false | true ",
    "span           | %X{dd.span_id} @%m%n                          | true    | true  | false",
    "all            | %X @%m%n                                      | false   | false | false",
    "empty MDC      | %X{} @%m%n                                    | true    | true  | true ",
    "key list       | %X{dd.service, dd.trace_id, dd.span_id} @%m%n | false   | false | false",
    "partial list   | %mdc{dd.service, dd.trace_id} @%m%n           | false   | false | true ",
    "upper alias    | %MDC{dd.span_id} @%m%n                        | true    | true  | false",
    "formatted      | %-32X{dd.trace_id} @%m%n                      | true    | false | true ",
    "nested         | %style{%X{dd.trace_id}}{red} @%m%n            | true    | false | true ",
    "nested options | %replace{%m}{a}{%X{dd.trace_id}} @%m%n        | true    | true  | true ",
    "escaped        | %%X{dd.trace_id} @%m%n                        | true    | true  | true ",
    "literal keys   | dd.service @dd.trace_id dd.span_id %m%n       | true    | true  | true ",
    "message only   | %m@%n                                         | true    | true  | true "
  })
  void injectsOnlyMissingLog4j2Converters(
      String template, boolean service, boolean trace, boolean span) {
    String fields = (service ? SERVICE : "") + (trace ? TRACE : "") + (span ? SPAN : "");
    String expected =
        template.replace("@", template.startsWith("%m@") ? " " + fields.trim() : fields);
    String result =
        LogPatternInjector.injectLog4j(template.replace("@", ""), LogPatternInjector.Format.LOG4J2);
    assertEquals(expected, result);
    assertEquals(result, LogPatternInjector.injectLog4j(result, LogPatternInjector.Format.LOG4J2));
  }

  @TableTest({
    "Scenario | Pattern                        | Expected prefix          ",
    "pipe     | '%d{HH:mm:ss}|%m%n'            | '%d{HH:mm:ss}|'          ",
    "comma    | %d{HH:mm:ss},%m%n              | %d{HH:mm:ss},            ",
    "style    | %style{%d{HH:mm:ss}}{red} %m%n | %style{%d{HH:mm:ss}}{red}",
    "percent  | %% %m%n                        | %%                       "
  })
  void preservesLog4j2Prefix(String pattern, String expectedPrefix) {
    // Trailing spaces in table cells are trimmed by the table parser.
    if (!expectedPrefix.endsWith("|") && !expectedPrefix.endsWith(",")) {
      expectedPrefix += " ";
    }
    assertEquals(
        expectedPrefix + SERVICE + TRACE + SPAN + "%m%n",
        LogPatternInjector.injectLog4j(pattern, LogPatternInjector.Format.LOG4J2));
  }

  @Test
  void doesNotMistakeLog4j1AliasesOrCommaKeysForConverters() {
    String source = "%mdc{dd.trace_id} %X{dd.service,dd.span_id}%n";
    assertEquals(
        "%mdc{dd.trace_id} " + SERVICE + TRACE + SPAN + "%X{dd.service,dd.span_id}%n",
        LogPatternInjector.injectLog4j(source, LogPatternInjector.Format.LOG4J1_LEGACY));
  }

  @Test
  void earlyLog4j2DoesNotSupportKeyLists() {
    String source = "%X{dd.service,dd.trace_id,dd.span_id} %m%n";
    assertEquals(
        "%X{dd.service,dd.trace_id,dd.span_id} " + SERVICE + TRACE + SPAN + "%m%n",
        LogPatternInjector.injectLog4j(source, LogPatternInjector.Format.LOG4J2_LEGACY));
  }

  @Test
  void leavesNullEmptyAndMalformedPatternsAlone() {
    assertSame(null, LogPatternInjector.injectLog4j(null, LogPatternInjector.Format.LOG4J2));
    assertSame("", LogPatternInjector.injectLog4j("", LogPatternInjector.Format.LOG4J2));
    String invalid = "%d{not-closed";
    assertSame(invalid, LogPatternInjector.injectLog4j(invalid, LogPatternInjector.Format.LOG4J2));
  }
}
