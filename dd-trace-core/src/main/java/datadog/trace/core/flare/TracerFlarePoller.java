package datadog.trace.core.flare;

import com.squareup.moshi.Json;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import datadog.communication.ddagent.SharedCommunicationObjects;
import datadog.remoteconfig.ConfigurationChangesListener.PollingRateHinter;
import datadog.remoteconfig.ConfigurationPoller;
import datadog.remoteconfig.Product;
import datadog.remoteconfig.state.ParsedConfigKey;
import datadog.remoteconfig.state.ProductListener;
import datadog.trace.api.Config;
import datadog.trace.api.DynamicConfig;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import okio.Okio;

public final class TracerFlarePoller {
  private static final String FLARE_LOG_LEVEL_PREFIX = "flare-log-level.";

  private final DynamicConfig dynamicConfig;

  private Runnable stopPreparer;
  private Runnable stopSubmitter;

  private TracerFlareService tracerFlareService;

  public TracerFlarePoller(DynamicConfig dynamicConfig) {
    this.dynamicConfig = dynamicConfig;
  }

  public void start(Config config, SharedCommunicationObjects sco) {
    stopPreparer = new Preparer().register(config, sco);
    stopSubmitter = new Submitter().register(config, sco);

    tracerFlareService = new TracerFlareService(dynamicConfig, sco.okHttpClient, sco.agentUrl);
  }

  public void stop() {
    if (null != stopPreparer) {
      stopPreparer.run();
    }
    if (null != stopSubmitter) {
      stopSubmitter.run();
    }
  }

  final class Preparer implements ProductListener {
    private final JsonAdapter<AgentConfigLayer> AGENT_CONFIG_LAYER_ADAPTER;

    {
      Moshi MOSHI = new Moshi.Builder().build();
      AGENT_CONFIG_LAYER_ADAPTER = MOSHI.adapter(AgentConfigLayer.class);
    }

    public Runnable register(Config config, SharedCommunicationObjects sco) {
      ConfigurationPoller poller = sco.configurationPoller(config);
      if (null != poller) {
        poller.addListener(Product.AGENT_CONFIG, this);
        return poller::stop;
      } else {
        return null;
      }
    }

    @Override
    public void accept(ParsedConfigKey configKey, byte[] content, PollingRateHinter hinter)
        throws IOException {
      if (configKey.getConfigId().startsWith(FLARE_LOG_LEVEL_PREFIX)) {
        AgentConfigLayer agentConfigLayer =
            AGENT_CONFIG_LAYER_ADAPTER.fromJson(
                Okio.buffer(Okio.source(new ByteArrayInputStream(content))));
        if (null != agentConfigLayer
            && null != agentConfigLayer.config
            && null != agentConfigLayer.config.logLevel) {
          prepareTracerFlare(agentConfigLayer.config.logLevel);
        } else {
          cancelTracerFlare();
        }
      }
    }

    @Override
    public void remove(ParsedConfigKey configKey, PollingRateHinter hinter) {
      if (configKey.getConfigId().startsWith(FLARE_LOG_LEVEL_PREFIX)) {
        cancelTracerFlare();
      }
    }

    @Override
    public void commit(PollingRateHinter hinter) {}
  }

  final class Submitter implements ProductListener {
    private final JsonAdapter<AgentTask> AGENT_TASK_ADAPTER;

    {
      Moshi MOSHI = new Moshi.Builder().build();
      AGENT_TASK_ADAPTER = MOSHI.adapter(AgentTask.class);
    }

    public Runnable register(Config config, SharedCommunicationObjects sco) {
      ConfigurationPoller poller = sco.configurationPoller(config);
      if (null != poller) {
        poller.addListener(Product.AGENT_TASK, this);
        return poller::stop;
      } else {
        return null;
      }
    }

    @Override
    public void accept(ParsedConfigKey configKey, byte[] content, PollingRateHinter hinter)
        throws IOException {
      AgentTask agentTask =
          AGENT_TASK_ADAPTER.fromJson(Okio.buffer(Okio.source(new ByteArrayInputStream(content))));

      if (null != agentTask
          && null != agentTask.args
          && "tracer_flare".equals(agentTask.taskType)) {
        submitTracerFlare(agentTask.args);
      }
    }

    @Override
    public void remove(ParsedConfigKey configKey, PollingRateHinter hinter) {}

    @Override
    public void commit(PollingRateHinter hinter) {}
  }

  void prepareTracerFlare(String logLevel) {
    tracerFlareService.prepareTracerFlare(logLevel);
  }

  void cancelTracerFlare() {
    tracerFlareService.cancelTracerFlare();
  }

  void submitTracerFlare(AgentTaskArgs args) {
    tracerFlareService.sendFlare(args.caseId, args.userHandle, args.hostname);
  }

  static final class AgentConfigLayer {
    @Json(name = "name")
    public String name;

    @Json(name = "config")
    public AgentConfig config;
  }

  static final class AgentConfig {
    @Json(name = "log_level")
    public String logLevel;
  }

  static final class AgentTask {
    @Json(name = "task_type")
    public String taskType;

    @Json(name = "args")
    public AgentTaskArgs args;
  }

  static final class AgentTaskArgs {
    @Json(name = "case_id")
    public String caseId;

    @Json(name = "user_handle")
    public String userHandle;

    @Json(name = "hostname")
    public String hostname;
  }
}
