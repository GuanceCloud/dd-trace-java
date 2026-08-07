package datadog.trace.api;

import static datadog.trace.api.config.TracerConfig.SERVICE_MAPPING;
import static datadog.trace.api.config.TracerConfig.SERVICE_MAPPING_FILE;
import static datadog.trace.junit.utils.config.WithConfigExtension.injectSysConfig;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import datadog.trace.junit.utils.config.WithConfigExtension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(WithConfigExtension.class)
class ServiceMappingFileConfigTest {

  @TempDir Path tempDir;

  @Test
  void readsServiceMappingFromFile() throws IOException {
    Path mappingsFile = writeMappingsFile("orders:checkout\nlegacy-db:database");

    injectSysConfig(SERVICE_MAPPING_FILE, mappingsFile.toString());

    assertEquals(
        mapOf("orders", "checkout", "legacy-db", "database"), Config.get().getServiceMapping());
  }

  @Test
  void mergesInlineServiceMappingOverFileMapping() throws IOException {
    Path mappingsFile = writeMappingsFile("shared:file-value\nfile-only:file-service");

    injectSysConfig(SERVICE_MAPPING_FILE, mappingsFile.toString());
    injectSysConfig(SERVICE_MAPPING, "shared:inline-value,inline-only:inline-service");

    assertEquals(
        mapOf(
            "shared",
            "inline-value",
            "file-only",
            "file-service",
            "inline-only",
            "inline-service"),
        Config.get().getServiceMapping());
  }

  @Test
  void keepsInlineServiceMappingWhenFileCannotBeRead() {
    injectSysConfig(SERVICE_MAPPING_FILE, tempDir.resolve("missing-service-mapping").toString());
    injectSysConfig(SERVICE_MAPPING, "orders:checkout");

    assertEquals(mapOf("orders", "checkout"), Config.get().getServiceMapping());
  }

  private Path writeMappingsFile(String content) throws IOException {
    Path file = tempDir.resolve("service-mapping.txt");
    Files.write(file, content.getBytes(UTF_8));
    return file;
  }

  private static Map<String, String> mapOf(String... values) {
    Map<String, String> map = new LinkedHashMap<>();
    for (int i = 0; i < values.length; i += 2) {
      map.put(values[i], values[i + 1]);
    }
    return map;
  }
}
