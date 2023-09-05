package com.datadog.profiling.controller.pyroscope;

import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import io.pyroscope.javaagent.PyroscopeAgent;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.labels.LabelsSet;
import io.pyroscope.labels.ScopedContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PyroscopeProfiler {
  private static final Logger log = LoggerFactory.getLogger(PyroscopeProfiler.class);
  private static final String LABEL_PROFILE_ID = "profile_id";
  private static final String LABEL_SPAN_NAME = "span_name";

  private static final String ATTRIBUTE_KEY_PROFILE_ID = "pyroscope.profile.id";
  private static final String ATTRIBUTE_KEY_PROFILE_URL = "pyroscope.profile.url";
  private static final String ATTRIBUTE_KEY_PROFILE_BASELINE_URL = "pyroscope.profile.baseline.url";
  private static final String ATTRIBUTE_KEY_PROFILE_DIFF_URL = "pyroscope.profile.diff.url";

  private final Map<String, PyroscopeContextHolder> pyroscopeContexts = new ConcurrentHashMap<>();

  private static final String OPERATION = "_dd.trace.operation";
  private static final class Singleton {
    private static final PyroscopeProfiler INSTANCE = newInstance();
  }

  static PyroscopeProfiler newInstance() {
    return new PyroscopeProfiler();
  }

  private PyroscopeProfiler() {
    log.info("初始化 pyroscopeAgent");
    Config pyroConfig = Config.build();
//    PyroscopeAgent.start(pyroConfig);
    PyroscopeAgent.start(
        new PyroscopeAgent.Options.Builder(pyroConfig)
        .setExporter(new DDtraceExporter(pyroConfig))
//        .setScheduler(new DataDogContinuousProfilingScheduler(pyroConfig,new DDtraceExporter())
            .build());
  }
  public static PyroscopeProfiler getInstance() {
    return Singleton.INSTANCE;
  }


  public void start(AgentSpan span){
    log.info("start span------------span.getSpanId():{}",span.getSpanId());
    Map<String, String> labels = new HashMap<>();
    String profileId = String.valueOf(span.getSpanId());
    labels.put(LABEL_PROFILE_ID, profileId);
    labels.put(LABEL_SPAN_NAME, span.getSpanName().toString());

    ScopedContext pyroscopeContext = new ScopedContext(new LabelsSet(labels));
    span.setAttribute(ATTRIBUTE_KEY_PROFILE_ID, profileId);
    long now = now();
    PyroscopeContextHolder pyroscopeContextHolder = new PyroscopeContextHolder(profileId, pyroscopeContext, now);
    pyroscopeContexts.put(profileId, pyroscopeContextHolder);
    //todo
//    if (configuration.optimisticTimestamps) {
//      long optimisticEnd = now + TimeUnit.HOURS.toMillis(1);
//      if (configuration.addProfileURL) {
//        span.setAttribute(ATTRIBUTE_KEY_PROFILE_URL, buildProfileUrl(pyroscopeContextHolder, optimisticEnd));
//      }
//      if (configuration.addProfileBaselineURLs) {
//        String q = buildComparisonQuery(pyroscopeContextHolder, optimisticEnd);
//        span.setAttribute(ATTRIBUTE_KEY_PROFILE_BASELINE_URL, configuration.pyroscopeEndpoint + "/comparison?" + q);
//        span.setAttribute(ATTRIBUTE_KEY_PROFILE_DIFF_URL, configuration.pyroscopeEndpoint + "/comparison-diff?" + q);
//      }
//    }
  }

//  private String buildComparisonQuery(PyroscopeContextHolder pyroscopeContext, long untilMilis) {
//    StringBuilder qb = new StringBuilder();
//    pyroscopeContext.ctx.forEach((k, v) -> {
//      if (k.equals(LABEL_PROFILE_ID)) {
//        return;
//      }
//      if (configuration.profileBaselineLabels.containsKey(k)) {
//        return;
//      }
//      writeLabel(qb, k, v);
//    });
//    for (Map.Entry<String, String> it : configuration.profileBaselineLabels.entrySet()) {
//      writeLabel(qb, it.getKey(), it.getValue());
//    }
//    StringBuilder query = new StringBuilder();
//    String from = Long.toString(pyroscopeContext.startTimeMillis - 3600000);
//    String until = Long.toString(untilMilis);
//    String baseLineQuery = String.format("%s{%s}", configuration.appName, qb.toString());
//    query.append("query=").append(urlEncode(baseLineQuery));
//    query.append("&from=").append(from);
//    query.append("&until=").append(until);
//
//    query.append("&leftQuery=").append(urlEncode(baseLineQuery));
//    query.append("&leftFrom=").append(from);
//    query.append("&leftUntil=").append(until);
//
//    query.append("&rightQuery=").append(urlEncode(String.format("%s{%s=\"%s\"}", configuration.appName, LABEL_PROFILE_ID, pyroscopeContext.profileId)));
//    query.append("&rightFrom=").append(from);
//    query.append("&rightUntil=").append(until);
//    return query.toString();
//  }


  public void finish(AgentSpan span) {
    Object profileId = span.getTag(ATTRIBUTE_KEY_PROFILE_ID);
    log.info("pyroscope_finished span:{}",profileId);
    if (profileId == null) {
      return;
    }
    PyroscopeContextHolder pyroscopeContext = pyroscopeContexts.remove(profileId.toString());
    if (pyroscopeContext == null) {
      return;
    }

    try {
      // todo need?
//      if (configuration.optimisticTimestamps) {
//        return;
//      }
//      if (sdkSpanInternals == null) {
//        return;
//      }
//      if (!(span instanceof ReadWriteSpan)) {
//        return;
//      }
//      long now = now();
//      if (configuration.addProfileURL) {
//        sdkSpanInternals.setAttribute(span, ATTRIBUTE_KEY_PROFILE_URL, buildProfileUrl(pyroscopeContext, now));
//      }
//      if (configuration.addProfileBaselineURLs) {
//        String q = buildComparisonQuery(pyroscopeContext, now);
//        sdkSpanInternals.setAttribute(span, ATTRIBUTE_KEY_PROFILE_BASELINE_URL, configuration.pyroscopeEndpoint + "/comparison?" + q);
//        sdkSpanInternals.setAttribute(span, ATTRIBUTE_KEY_PROFILE_DIFF_URL, configuration.pyroscopeEndpoint + "/comparison-diff?" + q);
//      }
    } finally {
      pyroscopeContext.ctx.close();
    }

  }


  private void writeLabel(StringBuilder sb, String k, String v) {
    if (sb.length() != 0) {
      sb.append(",");
    }
    sb.append(k).append("=\"").append(v).append("\"");
  }

//  private String buildProfileUrl(PyroscopeContextHolder pyroscopeContext, long untilMillis) {
//    String query = String.format("%s{%s=\"%s\"}", configuration.appName, LABEL_PROFILE_ID, pyroscopeContext.profileId);
//    return String.format("%s?query=%s&from=%d&until=%d", configuration.pyroscopeEndpoint,
//        urlEncode(query),
//        pyroscopeContext.startTimeMillis,
//        untilMillis
//    );
//  }

  private static long now() {
    return System.currentTimeMillis();
  }

  private static String urlEncode(String query) {
    try {
      return URLEncoder.encode(query, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static class PyroscopeContextHolder {
    final String profileId;
    final ScopedContext ctx;
    final long startTimeMillis;

    PyroscopeContextHolder(String profileId, ScopedContext ctx, long startTimeMillis) {
      this.profileId = profileId;
      this.ctx = ctx;
      this.startTimeMillis = startTimeMillis;
    }
  }
}
