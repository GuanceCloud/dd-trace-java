package datadog.trace.instrumentation.junit5;

import datadog.trace.api.civisibility.config.TestSourceData;
import datadog.trace.api.civisibility.telemetry.tag.TestFrameworkInstrumentation;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;

public class SpockTracingListener implements EngineExecutionListener {

  private final String testFramework;
  private final String testFrameworkVersion;

  public SpockTracingListener(TestEngine testEngine) {
    testFramework = testEngine.getId();
    testFrameworkVersion = testEngine.getVersion().orElse(null);
  }

  @Override
  public void dynamicTestRegistered(TestDescriptor testDescriptor) {
    // no op
  }

  @Override
  public void reportingEntryPublished(TestDescriptor testDescriptor, ReportEntry entry) {
    // no op
  }

  @Override
  public void executionStarted(final TestDescriptor descriptor) {
    if (descriptor.isContainer()) {
      containerExecutionStarted(descriptor);
    } else if (descriptor.isTest()) {
      testCaseExecutionStarted(descriptor);
    }
  }

  @Override
  public void executionFinished(
      TestDescriptor descriptor, TestExecutionResult testExecutionResult) {
    if (descriptor.isContainer()) {
      containerExecutionFinished(descriptor, testExecutionResult);
    } else if (descriptor.isTest()) {
      testCaseExecutionFinished(descriptor, testExecutionResult);
    }
  }

  private void containerExecutionStarted(final TestDescriptor suiteDescriptor) {
    if (!SpockUtils.isSpec(suiteDescriptor)) {
      return;
    }

    Class<?> testClass = JUnitPlatformUtils.getJavaClass(suiteDescriptor);
    String testSuiteName =
        testClass != null ? testClass.getName() : suiteDescriptor.getLegacyReportingName();
    List<String> tags =
        suiteDescriptor.getTags().stream().map(TestTag::getName).collect(Collectors.toList());
    TestEventsHandlerHolder.TEST_EVENTS_HANDLER.onTestSuiteStart(
        suiteDescriptor,
        testSuiteName,
        testFramework,
        testFrameworkVersion,
        testClass,
        tags,
        false,
        TestFrameworkInstrumentation.SPOCK,
        null);
  }

  private void containerExecutionFinished(
      final TestDescriptor suiteDescriptor, final TestExecutionResult testExecutionResult) {
    if (!SpockUtils.isSpec(suiteDescriptor)) {
      return;
    }

    Throwable throwable = testExecutionResult.getThrowable().orElse(null);
    if (throwable != null) {
      if (JUnitPlatformUtils.isAssumptionFailure(throwable)) {

        String reason = throwable.getMessage();
        TestEventsHandlerHolder.TEST_EVENTS_HANDLER.onTestSuiteSkip(suiteDescriptor, reason);

        for (TestDescriptor child : suiteDescriptor.getChildren()) {
          executionSkipped(child, reason);
        }

      } else {
        TestEventsHandlerHolder.TEST_EVENTS_HANDLER.onTestSuiteFailure(suiteDescriptor, throwable);
      }
    }

    TestEventsHandlerHolder.TEST_EVENTS_HANDLER.onTestSuiteFinish(suiteDescriptor, null);
  }

  private void testCaseExecutionStarted(final TestDescriptor testDescriptor) {
    TestSource testSource = testDescriptor.getSource().orElse(null);
    if (testSource instanceof MethodSource) {
      testMethodExecutionStarted(testDescriptor, (MethodSource) testSource);
    }
  }

  private void testMethodExecutionStarted(TestDescriptor testDescriptor, MethodSource testSource) {
    TestDescriptor suiteDescriptor = SpockUtils.getSpecDescriptor(testDescriptor);
    String displayName = testDescriptor.getDisplayName();
    String testParameters = JUnitPlatformUtils.getParameters(testSource, displayName);
    List<String> tags =
        testDescriptor.getTags().stream().map(TestTag::getName).collect(Collectors.toList());
    TestSourceData testSourceData = SpockUtils.toTestSourceData(testDescriptor);

    TestEventsHandlerHolder.TEST_EVENTS_HANDLER.onTestStart(
        suiteDescriptor,
        testDescriptor,
        displayName,
        testFramework,
        testFrameworkVersion,
        testParameters,
        tags,
        testSourceData,
        JUnitPlatformUtils.isRetry(testDescriptor),
        null);
  }

  private void testCaseExecutionFinished(
      final TestDescriptor testDescriptor, final TestExecutionResult testExecutionResult) {
    TestSource testSource = testDescriptor.getSource().orElse(null);
    if (testSource instanceof MethodSource) {
      testMethodExecutionFinished(testDescriptor, testExecutionResult);
    }
  }

  private static void testMethodExecutionFinished(
      TestDescriptor testDescriptor, TestExecutionResult testExecutionResult) {
    Throwable throwable = testExecutionResult.getThrowable().orElse(null);
    if (throwable != null) {
      if (JUnitPlatformUtils.isAssumptionFailure(throwable)) {
        TestEventsHandlerHolder.TEST_EVENTS_HANDLER.onTestSkip(
            testDescriptor, throwable.getMessage());
      } else {
        TestEventsHandlerHolder.TEST_EVENTS_HANDLER.onTestFailure(testDescriptor, throwable);
      }
    }
    TestEventsHandlerHolder.TEST_EVENTS_HANDLER.onTestFinish(testDescriptor, null);
  }

  @Override
  public void executionSkipped(final TestDescriptor descriptor, final String reason) {
    TestSource testSource = descriptor.getSource().orElse(null);
    if (testSource instanceof ClassSource) {
      // The annotation @Disabled is kept at type level.
      containerExecutionSkipped(descriptor, reason);

    } else if (testSource instanceof MethodSource) {
      // The annotation @Disabled is kept at method level.
      testMethodExecutionSkipped(descriptor, (MethodSource) testSource, reason);
    }
  }

  private void containerExecutionSkipped(
      final TestDescriptor suiteDescriptor, final String reason) {
    if (!SpockUtils.isSpec(suiteDescriptor)) {
      return;
    }

    Class<?> testClass = JUnitPlatformUtils.getJavaClass(suiteDescriptor);
    String testSuiteName =
        testClass != null ? testClass.getName() : suiteDescriptor.getLegacyReportingName();
    List<String> tags =
        suiteDescriptor.getTags().stream().map(TestTag::getName).collect(Collectors.toList());

    TestEventsHandlerHolder.TEST_EVENTS_HANDLER.onTestSuiteStart(
        suiteDescriptor,
        testSuiteName,
        testFramework,
        testFrameworkVersion,
        testClass,
        tags,
        false,
        TestFrameworkInstrumentation.SPOCK,
        null);
    TestEventsHandlerHolder.TEST_EVENTS_HANDLER.onTestSuiteSkip(suiteDescriptor, reason);

    for (TestDescriptor child : suiteDescriptor.getChildren()) {
      executionSkipped(child, reason);
    }

    TestEventsHandlerHolder.TEST_EVENTS_HANDLER.onTestSuiteFinish(suiteDescriptor, null);
  }

  private void testMethodExecutionSkipped(
      final TestDescriptor testDescriptor, final MethodSource methodSource, final String reason) {
    TestDescriptor suiteDescriptor = SpockUtils.getSpecDescriptor(testDescriptor);
    String displayName = testDescriptor.getDisplayName();
    String testParameters = JUnitPlatformUtils.getParameters(methodSource, displayName);
    List<String> tags =
        testDescriptor.getTags().stream().map(TestTag::getName).collect(Collectors.toList());
    TestSourceData testSourceData = SpockUtils.toTestSourceData(testDescriptor);

    TestEventsHandlerHolder.TEST_EVENTS_HANDLER.onTestIgnore(
        suiteDescriptor,
        testDescriptor,
        displayName,
        testFramework,
        testFrameworkVersion,
        testParameters,
        tags,
        testSourceData,
        reason);
  }
}
