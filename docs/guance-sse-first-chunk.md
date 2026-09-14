# SSE 请求到首块耗时

`extension_data.metrics.stream.first_chunk.ms` 表示从 Netty 开始处理并创建 HTTP 请求 span，到采集 handler 收到第一个非空 HTTP 正文块的耗时，单位为毫秒，保留小数。

此修正将同名字段的起点从“收到响应头后”改为“请求开始”。响应头前的等待时间现在包含在指标中。历史版本和修正版本的统计口径不同，比较历史数据时需要按 SDK 版本区分。

首块指非空 `HttpContent`，无需等待完整 SSE 事件解析完成。非空心跳或注释同样属于首块；该字段不是首 token 耗时。请求开始位于 Netty HTTP 请求处理阶段，不保证包含更上层的连接池排队、DNS 或建连等待。

空正文块和空正文结束标记不触发首块计时，也不增加 `stream.chunk_count`。没有收到非空正文就结束的 SSE 流保留首块字段缺失，chunk_count 为 0。如果请求起点不可用，也不会生成错误的耗时值。

stream span 的生命周期仍然从响应头处理阶段延续到流结束，因此首块耗时可能大于 stream span 的 duration；两者起点不同。`stream.chunk_count` 现在表示非空 Netty 正文块数量，仍不等于 SSE 事件数或 token 数。

## 构建与使用

没有新增配置项。使用修正后的 javaagent JAR 替换原 JAR，并重启 Java 服务即可应用。

```bash
./gradlew :dd-java-agent:instrumentation:netty:netty-4.1:test \
  --tests '*NettyClientResponseStreamTest'
./gradlew :dd-java-agent:shadowJar
```

构建结果位于 `dd-java-agent/build/libs/`。启动方式保持现有参数不变，将 `-javaagent:` 的路径替换为修正版本：

```bash
java -javaagent:/path/to/fixed-dd-java-agent.jar [现有 JVM 参数] -jar application.jar
```

验证时选择 `component=netty-client`、`stream.type=sse`、operation 为 `netty.client.stream` 的 Span，检查首块字段是否包含响应头之前的等待，确认展示及导出保留小数。无正文响应应缺失首块字段且 chunk_count 为 0，不能将缺失值补成 0。
