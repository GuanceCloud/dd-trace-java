# Logback 日志 pattern 自动注入

开启下列 Agent 配置后，无需修改 `logback.xml` 或 `logback-spring.xml`，即可为 Logback 纯文本日志补充链路关联字段：

```shell
java -javaagent:dd-java-agent.jar \
  -Ddd.logs.pattern.inject=true \
  -jar app.jar
```

也可以设置环境变量 `DD_LOGS_PATTERN_INJECT=true`。
`dd.logs.pattern.inject` 默认关闭，仅适用于 Logback 的 `PatternLayout`（包括 `PatternLayoutEncoder` 创建的 layout）。
提供 MDC 关联值的 `dd.logs.injection` 默认开启；如果已有配置关闭了日志关联，需要先重新开启。

例如，原 pattern 为：

```text
%d{HH:mm:ss.SSS} [%thread] %-5level %logger - %msg%n
```

开启后等效为：

```text
%d{HH:mm:ss.SSS} dd.service=%X{dd.service} dd.trace_id=%X{dd.trace_id} [%thread] %-5level %logger - %msg%n
```

自动补充的字段依次为 `dd.service` 和 `dd.trace_id`；不自动添加 span ID，原 pattern 中已有的 span ID 占位符保持原样。
服务名来自探针配置，例如 `-Ddd.service=order-service` 或 `DD_SERVICE=order-service`。

关联字段放在第一个完整字段之后，通常就是日期字段之后，日志行仍以原日期开头。
日期格式内部的空格、日期外的方括号及颜色等组合 converter 会完整保留；
第一个字段后的原有空白、管道符、逗号或分号分隔符也会保留在关联字段之前。
如果 pattern 只有 `%msg%n`，则在消息之后、换行之前添加关联字段。

只补充缺失的字段；已有 `%X{dd.trace_id}`、`%mdc{dd.service}` 等占位符时不会重复添加。
输出全部 MDC 的 `%X` / `%mdc` 也会保留原格式。识别支持格式修饰符、默认值及嵌套 converter；
转义后的占位符和其他 converter 的选项文本不视为已输出字段。layout 重新启动时不会重复添加。

改写发生在 layout 初始化时，原 pattern 的换行、异常输出及其他内容保持原样。
字段值由现有日志关联插桩提供，异步 appender 使用日志产生时捕获的上下文。
此开关不会自行生成 trace ID；没有有效 span 且应用未自行提供 MDC 值时，trace ID 为空，服务名仍可输出。
关闭日志关联后，Agent 不再自动提供这些 MDC 值，应用自行写入的 MDC 值仍按原有规则输出。

此配置对所有 Logback classic `PatternLayout` 实例生效，包括自定义 encoder 内部使用的实例。
JSON 日志通常只需开启 MDC 关联，无需启用此开关。
`dd.logs.pattern.replace` / `dd.logs.pattern` 的现有行为保持不变。

需求跟踪：[Issue #184](https://github.com/GuanceCloud/dd-trace-java/issues/184)。
