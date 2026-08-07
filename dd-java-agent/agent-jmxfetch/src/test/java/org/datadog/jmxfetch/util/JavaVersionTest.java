package org.datadog.jmxfetch.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class JavaVersionTest {

  @ParameterizedTest
  @CsvSource({
    "1.7.0_80, 7",
    "1.8.0_402, 8",
    "9-ea, 9",
    "11.0.22+7, 11",
    "17-ea, 17",
    "17.0.7+8-LTS-224, 17",
    "21, 21"
  })
  void parsesMajorJavaVersion(String version, int expected) {
    assertEquals(expected, JavaVersion.parseMajorJavaVersion(version));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "ea", "invalid"})
  void returnsZeroForUnparseableVersion(String version) {
    assertEquals(0, JavaVersion.parseMajorJavaVersion(version));
  }
}
