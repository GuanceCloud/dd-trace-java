package org.datadog.jmxfetch.util;

public final class JavaVersion {
  private static final int JAVA_VERSION = getMajorJavaVersion();

  private JavaVersion() {}

  public static boolean atLeastJava(int expectedVersion) {
    return expectedVersion <= JAVA_VERSION;
  }

  private static int getMajorJavaVersion() {
    String version = getSystemProperty("java.specification.version");
    int majorVersion = parseMajorJavaVersion(version);
    if (majorVersion > 0) {
      return majorVersion;
    }

    version = getSystemProperty("java.version");
    majorVersion = parseMajorJavaVersion(version);
    return majorVersion > 0 ? majorVersion : 7;
  }

  private static String getSystemProperty(String name) {
    try {
      return System.getProperty(name);
    } catch (Throwable ignored) {
      return null;
    }
  }

  static int parseMajorJavaVersion(String version) {
    if (version == null || version.isEmpty()) {
      return 0;
    }

    int first = parseNumber(version, 0);
    if (first != 1) {
      return first;
    }

    int separator = version.indexOf('.');
    return separator >= 0 ? parseNumber(version, separator + 1) : first;
  }

  private static int parseNumber(String version, int start) {
    int value = 0;
    for (int i = start; i < version.length(); i++) {
      char ch = version.charAt(i);
      if (ch < '0' || ch > '9') {
        break;
      }
      value = value * 10 + ch - '0';
    }
    return value;
  }
}
