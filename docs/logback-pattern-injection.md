# 日志 pattern 自动注入

开启下列 Agent 配置后，无需修改日志配置文件，即可为 Logback、Log4j 1.x 和 Log4j2 纯文本日志补充链路关联字段：

```shell
java -javaagent:dd-java-agent.jar \
  -Ddd.logs.pattern.inject=true \
  -jar app.jar
```

也可以设置环境变量 `DD_LOGS_PATTERN_INJECT=true`。
`dd.logs.pattern.inject` 默认关闭，适用于 Logback classic 的 `PatternLayout`（包括 `PatternLayoutEncoder`）、Log4j 1.x 的 `PatternLayout` 和 Log4j2 的 `PatternLayout`。
提供 MDC 关联值的 `dd.logs.injection` 默认开启；如果已有配置关闭了日志关联，需要先重新开启。

例如，Logback 的原 pattern 为：

```text
%d{HH:mm:ss.SSS} [%thread] %-5level %logger - %msg%n
```

开启后等效为：

```text
%d{HH:mm:ss.SSS} dd.service=%X{dd.service} dd.trace_id=%X{dd.trace_id} dd.span_id=%X{dd.span_id} [%thread] %-5level %logger - %msg%n
```

自动补充的字段依次为 `dd.service`、`dd.trace_id` 和 `dd.span_id`。已存在的 converter 保留在原位置，只按上述顺序补充缺失字段。
服务名来自探针配置，例如 `-Ddd.service=order-service` 或 `DD_SERVICE=order-service`。

关联字段放在第一个完整字段之后，通常就是日期字段之后，日志行仍以原日期开头。
日期格式内部的空格、日期外的方括号及颜色等组合 converter 会完整保留；
第一个字段后的原有空白、管道符、逗号或分号分隔符也会保留在关联字段之前。
如果 pattern 只有 `%msg%n`（Log4j 1.x 为 `%m%n`），则在消息之后、换行之前添加关联字段。
Log4j 1.x 使用短参数，例如 `%d{HH:mm:ss.SSS} [%t] %-5p %c - %m%n`，同样在日期之后补充这三个 `%X{...}` 字段。

只补充缺失的字段；已有 `%X{dd.trace_id}`、`%X{dd.span_id}` 等占位符时不会重复添加。
Logback 和 Log4j2 的 `%mdc` 别名也可以识别；Log4j2 还支持 `%MDC`，支持多 key 的版本也能识别 `%X{dd.service,dd.trace_id,dd.span_id}`。
Log4j 1.x 的早期版本不支持全量 `%X`，这类 converter 不会阻止自动补充；Log4j2 的 `%X{}` 是空 key，也不视为全量 MDC。
输出全部 MDC 的 converter 保留原格式。识别支持格式修饰符和嵌套 converter，以及 Logback 的 `:-` 默认值；
转义后的占位符和其他 converter 的选项文本不视为已输出字段。layout 重新启动或复用增强后的 pattern 时不会重复添加。

改写发生在 layout 初始化时，原 pattern 的换行、异常输出及其他内容保持原样。
字段值由现有日志关联插桩提供，异步 appender 使用日志产生时捕获的上下文。
此开关不会自行生成 trace ID；没有有效 span 且应用未自行提供 MDC 值时，trace ID 和 span ID 为空，服务名仍可输出。
关闭日志关联后，Agent 不再自动提供这些 MDC 值，应用自行写入的 MDC 值仍按原有规则输出。

Logback 在 layout 启动时改写，Log4j 1.x 覆盖构造和 `setConversionPattern`，Log4j2 覆盖 Builder 和旧 `createLayout` 工厂最终使用的构造入口。
此配置只增强文本 event pattern，Log4j2 的 `PatternSelector` 自定义分支不在此范围。
JSON 日志通常只需开启 MDC 关联，无需启用此开关。
Log4j2 显式整段替换配置 `dd.logs.pattern.replace=true` / `dd.logs.pattern` 优先于自动补充，替换结果不会再追加字段。

需求跟踪：[Issue #184](https://github.com/GuanceCloud/dd-trace-java/issues/184)。
