package com.datadog.profiling.controller.pyroscope;

import io.pyroscope.javaagent.Snapshot;
import io.pyroscope.javaagent.api.Exporter;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.okhttp3.MediaType;
import io.pyroscope.okhttp3.OkHttpClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class DDtraceExporter implements Exporter {
  private static final Duration TIMEOUT = Duration.ofSeconds(10);//todo allow configuration
  private static final MediaType PROTOBUF = MediaType.parse("application/x-protobuf");

  final Config config;
  final OkHttpClient client;

  public DDtraceExporter(Config config) {
    this.config = config;
    this.client = new OkHttpClient.Builder()
        .connectTimeout(TIMEOUT)
        .readTimeout(TIMEOUT)
        .callTimeout(TIMEOUT)
        .build();

  }

  @Override
  public void export(Snapshot snapshot) {
    try {

      Path path = Files.createTempFile("dd-profiler-", ".jfr");
      System.out.println("create file"+path.toAbsolutePath().toString());
      Files.write(path,snapshot.data);
    } catch (final Exception e) {
      e.printStackTrace();
    }
  }

}
