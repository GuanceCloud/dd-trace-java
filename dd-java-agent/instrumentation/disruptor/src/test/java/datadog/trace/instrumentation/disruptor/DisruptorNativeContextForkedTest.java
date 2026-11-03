package datadog.trace.instrumentation.disruptor;

import datadog.trace.test.junit.utils.config.WithConfig;

@WithConfig(key = "runtime.context.map.per.store", value = "false")
class DisruptorNativeContextForkedTest extends DisruptorPropagationTest {}
