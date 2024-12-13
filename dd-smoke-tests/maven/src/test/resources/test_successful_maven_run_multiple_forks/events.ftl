[ {
  "type" : "test_session_end",
  "version" : 1,
  "content" : {
    "test_session_id" : ${content_test_session_id},
    "service" : "test-maven-service",
    "name" : "maven.test_session",
    "resource" : "Maven Smoke Tests Project",
    "start" : ${content_start},
    "duration" : ${content_duration},
    "error" : 0,
    "metrics" : {
      "process_id" : ${content_metrics_process_id},
      "test.itr.tests_skipping.count" : 1,
      "_dd.profiling.enabled" : 0,
      "_dd.trace_span_attribute_schema" : 0,
      "_dd.host.vcpu_count" : ${content_metrics__dd_host_vcpu_count},
      "test.code_coverage.lines_pct" : 67
    },
    "meta" : {
      "_dd.p.tid" : ${content_meta__dd_p_tid},
      "_dd.tracer_host" : ${content_meta__dd_tracer_host},
      "test.status" : "pass",
      "_dd.ci.itr.tests_skipped" : "true",
      "ci.workspace_path" : ${content_meta_ci_workspace_path},
      "language" : "jvm",
      "library_version" : ${content_meta_library_version},
      "span.kind" : "test_session_end",
      "runtime-id" : ${content_meta_runtime_id},
      "test.code_coverage.backfilled" : "true",
      "test.itr.tests_skipping.enabled" : "true",
      "test.type" : "test",
      "test_session.name" : "mvn -B test",
      "env" : "integration-test",
      "component" : "maven",
      "test.code_coverage.enabled" : "true",
      "test.toolchain" : ${content_meta_test_toolchain},
      "test.itr.tests_skipping.type" : "test",
      "test.command" : "mvn -B test",
      "test.framework_version" : "5.9.2",
      "test.framework" : "junit5",
      "runtime.name" : ${content_meta_runtime_name},
      "runtime.vendor" : ${content_meta_runtime_vendor},
      "runtime.version" : ${content_meta_runtime_version},
      "os.architecture" : ${content_meta_os_architecture},
      "os.platform" : ${content_meta_os_platform},
      "os.version" : ${content_meta_os_version}
    }
  }
}, {
  "type" : "test_module_end",
  "version" : 1,
  "content" : {
    "test_session_id" : ${content_test_session_id},
    "test_module_id" : ${content_test_module_id},
    "service" : "test-maven-service",
    "name" : "maven.test_module",
    "resource" : "Maven Smoke Tests Project maven-surefire-plugin default-test",
    "start" : ${content_start_2},
    "duration" : ${content_duration_2},
    "error" : 0,
    "metrics" : {
      "_dd.host.vcpu_count" : ${content_metrics__dd_host_vcpu_count_2},
      "test.itr.tests_skipping.count" : 1,
      "test.code_coverage.lines_pct" : 67
    },
    "meta" : {
      "_dd.p.tid" : ${content_meta__dd_p_tid_2},
      "test.type" : "test",
      "test.module" : "Maven Smoke Tests Project maven-surefire-plugin default-test",
      "test.status" : "pass",
      "_dd.ci.itr.tests_skipped" : "true",
      "test_session.name" : "mvn -B test",
      "ci.workspace_path" : ${content_meta_ci_workspace_path},
      "env" : "integration-test",
      "library_version" : ${content_meta_library_version},
      "component" : "maven",
      "test.code_coverage.enabled" : "true",
      "span.kind" : "test_module_end",
      "test.execution" : "maven-surefire-plugin:test:default-test",
      "test.itr.tests_skipping.type" : "test",
      "test.command" : "mvn -B test",
      "test.code_coverage.backfilled" : "true",
      "test.framework_version" : "5.9.2",
      "test.framework" : "junit5",
      "test.itr.tests_skipping.enabled" : "true",
      "runtime-id" : ${content_meta_runtime_id},
      "language" : "jvm",
      "runtime.name" : ${content_meta_runtime_name},
      "runtime.vendor" : ${content_meta_runtime_vendor},
      "runtime.version" : ${content_meta_runtime_version},
      "os.architecture" : ${content_meta_os_architecture},
      "os.platform" : ${content_meta_os_platform},
      "os.version" : ${content_meta_os_version}
    }
  }
}, {
  "type" : "span",
  "version" : 1,
  "content" : {
    "trace_id" : ${content_test_session_id},
    "span_id" : ${content_span_id},
    "parent_id" : ${content_test_session_id},
    "service" : "test-maven-service",
    "name" : "Maven_Smoke_Tests_Project_jacoco_maven_plugin_default",
    "resource" : "Maven_Smoke_Tests_Project_jacoco_maven_plugin_default",
    "start" : ${content_start_3},
    "duration" : ${content_duration_3},
    "error" : 0,
    "metrics" : { },
    "meta" : {
      "_dd.p.tid" : ${content_meta__dd_p_tid_3},
      "execution" : "default",
      "project" : "Maven Smoke Tests Project",
      "library_version" : ${content_meta_library_version},
      "env" : "integration-test",
      "plugin" : "jacoco-maven-plugin",
      "runtime-id" : ${content_meta_runtime_id},
      "language" : "jvm",
      "runtime.name" : ${content_meta_runtime_name},
      "runtime.vendor" : ${content_meta_runtime_vendor},
      "runtime.version" : ${content_meta_runtime_version},
      "os.architecture" : ${content_meta_os_architecture},
      "os.platform" : ${content_meta_os_platform},
      "os.version" : ${content_meta_os_version}
    }
  }
}, {
  "type" : "span",
  "version" : 1,
  "content" : {
    "trace_id" : ${content_test_session_id},
    "span_id" : ${content_span_id_2},
    "parent_id" : ${content_test_session_id},
    "service" : "test-maven-service",
    "name" : "Maven_Smoke_Tests_Project_maven_compiler_plugin_default_compile",
    "resource" : "Maven_Smoke_Tests_Project_maven_compiler_plugin_default_compile",
    "start" : ${content_start_4},
    "duration" : ${content_duration_4},
    "error" : 0,
    "metrics" : { },
    "meta" : {
      "_dd.p.tid" : ${content_meta__dd_p_tid_4},
      "execution" : "default-compile",
      "project" : "Maven Smoke Tests Project",
      "library_version" : ${content_meta_library_version},
      "env" : "integration-test",
      "plugin" : "maven-compiler-plugin",
      "runtime-id" : ${content_meta_runtime_id},
      "language" : "jvm",
      "runtime.name" : ${content_meta_runtime_name},
      "runtime.vendor" : ${content_meta_runtime_vendor},
      "runtime.version" : ${content_meta_runtime_version},
      "os.architecture" : ${content_meta_os_architecture},
      "os.platform" : ${content_meta_os_platform},
      "os.version" : ${content_meta_os_version}
    }
  }
}, {
  "type" : "span",
  "version" : 1,
  "content" : {
    "trace_id" : ${content_test_session_id},
    "span_id" : ${content_span_id_3},
    "parent_id" : ${content_test_session_id},
    "service" : "test-maven-service",
    "name" : "Maven_Smoke_Tests_Project_maven_compiler_plugin_default_testCompile",
    "resource" : "Maven_Smoke_Tests_Project_maven_compiler_plugin_default_testCompile",
    "start" : ${content_start_5},
    "duration" : ${content_duration_5},
    "error" : 0,
    "metrics" : { },
    "meta" : {
      "_dd.p.tid" : ${content_meta__dd_p_tid_5},
      "execution" : "default-testCompile",
      "project" : "Maven Smoke Tests Project",
      "library_version" : ${content_meta_library_version},
      "env" : "integration-test",
      "plugin" : "maven-compiler-plugin",
      "runtime-id" : ${content_meta_runtime_id},
      "language" : "jvm",
      "runtime.name" : ${content_meta_runtime_name},
      "runtime.vendor" : ${content_meta_runtime_vendor},
      "runtime.version" : ${content_meta_runtime_version},
      "os.architecture" : ${content_meta_os_architecture},
      "os.platform" : ${content_meta_os_platform},
      "os.version" : ${content_meta_os_version}
    }
  }
}, {
  "type" : "span",
  "version" : 1,
  "content" : {
    "trace_id" : ${content_test_session_id},
    "span_id" : ${content_span_id_4},
    "parent_id" : ${content_test_session_id},
    "service" : "test-maven-service",
    "name" : "Maven_Smoke_Tests_Project_maven_resources_plugin_default_resources",
    "resource" : "Maven_Smoke_Tests_Project_maven_resources_plugin_default_resources",
    "start" : ${content_start_6},
    "duration" : ${content_duration_6},
    "error" : 0,
    "metrics" : { },
    "meta" : {
      "_dd.p.tid" : ${content_meta__dd_p_tid_6},
      "execution" : "default-resources",
      "project" : "Maven Smoke Tests Project",
      "library_version" : ${content_meta_library_version},
      "env" : "integration-test",
      "plugin" : "maven-resources-plugin",
      "runtime-id" : ${content_meta_runtime_id},
      "language" : "jvm",
      "runtime.name" : ${content_meta_runtime_name},
      "runtime.vendor" : ${content_meta_runtime_vendor},
      "runtime.version" : ${content_meta_runtime_version},
      "os.architecture" : ${content_meta_os_architecture},
      "os.platform" : ${content_meta_os_platform},
      "os.version" : ${content_meta_os_version}
    }
  }
}, {
  "type" : "span",
  "version" : 1,
  "content" : {
    "trace_id" : ${content_test_session_id},
    "span_id" : ${content_span_id_5},
    "parent_id" : ${content_test_session_id},
    "service" : "test-maven-service",
    "name" : "Maven_Smoke_Tests_Project_maven_resources_plugin_default_testResources",
    "resource" : "Maven_Smoke_Tests_Project_maven_resources_plugin_default_testResources",
    "start" : ${content_start_7},
    "duration" : ${content_duration_7},
    "error" : 0,
    "metrics" : { },
    "meta" : {
      "_dd.p.tid" : ${content_meta__dd_p_tid_7},
      "execution" : "default-testResources",
      "project" : "Maven Smoke Tests Project",
      "library_version" : ${content_meta_library_version},
      "env" : "integration-test",
      "plugin" : "maven-resources-plugin",
      "runtime-id" : ${content_meta_runtime_id},
      "language" : "jvm",
      "runtime.name" : ${content_meta_runtime_name},
      "runtime.vendor" : ${content_meta_runtime_vendor},
      "runtime.version" : ${content_meta_runtime_version},
      "os.architecture" : ${content_meta_os_architecture},
      "os.platform" : ${content_meta_os_platform},
      "os.version" : ${content_meta_os_version}
    }
  }
}, {
  "type" : "test_suite_end",
  "version" : 1,
  "content" : {
    "test_session_id" : ${content_test_session_id},
    "test_module_id" : ${content_test_module_id},
    "test_suite_id" : ${content_test_suite_id},
    "service" : "test-maven-service",
    "name" : "junit.test_suite",
    "resource" : "datadog.smoke.TestSucceed",
    "start" : ${content_start_8},
    "duration" : ${content_duration_8},
    "error" : 0,
    "metrics" : {
      "_dd.host.vcpu_count" : ${content_metrics__dd_host_vcpu_count_3},
      "process_id" : ${content_metrics_process_id_2},
      "_dd.profiling.enabled" : 0,
      "test.source.end" : 18,
      "_dd.trace_span_attribute_schema" : 0,
      "test.source.start" : 7
    },
    "meta" : {
      "_dd.p.tid" : ${content_meta__dd_p_tid_8},
      "test.type" : "test",
      "_dd.tracer_host" : ${content_meta__dd_tracer_host},
      "test.source.file" : "src/test/java/datadog/smoke/TestSucceed.java",
      "test.module" : "Maven Smoke Tests Project maven-surefire-plugin default-test",
      "test.status" : "pass",
      "test_session.name" : "mvn -B test",
      "ci.workspace_path" : ${content_meta_ci_workspace_path},
      "language" : "jvm",
      "env" : "integration-test",
      "library_version" : ${content_meta_library_version},
      "component" : "junit",
      "span.kind" : "test_suite_end",
      "test.suite" : "datadog.smoke.TestSucceed",
      "runtime-id" : ${content_meta_runtime_id_2},
      "test.framework_version" : "5.9.2",
      "test.framework" : "junit5",
      "runtime.name" : ${content_meta_runtime_name},
      "runtime.vendor" : ${content_meta_runtime_vendor},
      "runtime.version" : ${content_meta_runtime_version},
      "os.architecture" : ${content_meta_os_architecture},
      "os.platform" : ${content_meta_os_platform},
      "os.version" : ${content_meta_os_version}
    }
  }
}, {
  "type" : "test",
  "version" : 2,
  "content" : {
    "trace_id" : ${content_trace_id},
    "span_id" : ${content_span_id_6},
    "parent_id" : ${content_parent_id},
    "test_session_id" : ${content_test_session_id},
    "test_module_id" : ${content_test_module_id},
    "test_suite_id" : ${content_test_suite_id},
    "service" : "test-maven-service",
    "name" : "junit.test",
    "resource" : "datadog.smoke.TestSucceed.test_succeed",
    "start" : ${content_start_9},
    "duration" : ${content_duration_9},
    "error" : 0,
    "metrics" : {
      "process_id" : ${content_metrics_process_id_2},
      "_dd.profiling.enabled" : 0,
      "_dd.trace_span_attribute_schema" : 0,
      "_dd.host.vcpu_count" : ${content_metrics__dd_host_vcpu_count_4},
      "test.source.end" : 12,
      "test.source.start" : 9
    },
    "meta" : {
      "_dd.p.tid" : ${content_meta__dd_p_tid_9},
      "_dd.tracer_host" : ${content_meta__dd_tracer_host},
      "test.source.file" : "src/test/java/datadog/smoke/TestSucceed.java",
      "test.source.method" : "test_succeed()V",
      "test.module" : "Maven Smoke Tests Project maven-surefire-plugin default-test",
      "test.status" : "pass",
      "ci.workspace_path" : ${content_meta_ci_workspace_path},
      "language" : "jvm",
      "library_version" : ${content_meta_library_version},
      "test.name" : "test_succeed",
      "span.kind" : "test",
      "test.suite" : "datadog.smoke.TestSucceed",
      "runtime-id" : ${content_meta_runtime_id_2},
      "test.type" : "test",
      "test_session.name" : "mvn -B test",
      "env" : "integration-test",
      "component" : "junit",
      "test.framework_version" : "5.9.2",
      "test.framework" : "junit5",
      "runtime.name" : ${content_meta_runtime_name},
      "runtime.vendor" : ${content_meta_runtime_vendor},
      "runtime.version" : ${content_meta_runtime_version},
      "os.architecture" : ${content_meta_os_architecture},
      "os.platform" : ${content_meta_os_platform},
      "os.version" : ${content_meta_os_version}
    }
  }
}, {
  "type" : "test",
  "version" : 2,
  "content" : {
    "trace_id" : ${content_trace_id_2},
    "span_id" : ${content_span_id_7},
    "parent_id" : ${content_parent_id},
    "test_session_id" : ${content_test_session_id},
    "test_module_id" : ${content_test_module_id},
    "test_suite_id" : ${content_test_suite_id},
    "service" : "test-maven-service",
    "name" : "junit.test",
    "resource" : "datadog.smoke.TestSucceed.test_to_skip_with_itr",
    "start" : ${content_start_10},
    "duration" : ${content_duration_10},
    "error" : 0,
    "metrics" : {
      "process_id" : ${content_metrics_process_id_2},
      "_dd.profiling.enabled" : 0,
      "_dd.trace_span_attribute_schema" : 0,
      "_dd.host.vcpu_count" : ${content_metrics__dd_host_vcpu_count_5},
      "test.source.end" : 17,
      "test.source.start" : 14
    },
    "meta" : {
      "_dd.p.tid" : ${content_meta__dd_p_tid_10},
      "_dd.tracer_host" : ${content_meta__dd_tracer_host},
      "test.source.file" : "src/test/java/datadog/smoke/TestSucceed.java",
      "test.source.method" : "test_to_skip_with_itr()V",
      "test.module" : "Maven Smoke Tests Project maven-surefire-plugin default-test",
      "test.status" : "skip",
      "ci.workspace_path" : ${content_meta_ci_workspace_path},
      "language" : "jvm",
      "library_version" : ${content_meta_library_version},
      "test.name" : "test_to_skip_with_itr",
      "span.kind" : "test",
      "test.suite" : "datadog.smoke.TestSucceed",
      "runtime-id" : ${content_meta_runtime_id_2},
      "test.type" : "test",
      "test.skip_reason" : "Skipped by Datadog Intelligent Test Runner",
      "test_session.name" : "mvn -B test",
      "env" : "integration-test",
      "component" : "junit",
      "test.skipped_by_itr" : "true",
      "test.framework_version" : "5.9.2",
      "test.framework" : "junit5",
      "runtime.name" : ${content_meta_runtime_name},
      "runtime.vendor" : ${content_meta_runtime_vendor},
      "runtime.version" : ${content_meta_runtime_version},
      "os.architecture" : ${content_meta_os_architecture},
      "os.platform" : ${content_meta_os_platform},
      "os.version" : ${content_meta_os_version}
    }
  }
} ]