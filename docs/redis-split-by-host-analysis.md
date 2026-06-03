# Redis `split-by-host` 问题分析与调整说明

## 1. 背景

客户希望在 APM 中将数据库或中间件的默认 service 名称按集群或节点区分，例如将默认的 `mysql`、`redis` 展示为类似 `mysql-1`、`redis-order-1` 的名称，以便区分不同业务或不同集群。

当前采用的方案是开启：

```text
DD_TRACE_DB_CLIENT_SPLIT_BY_HOST=true
```

其内部对应配置项为：

```text
trace.db.client.split-by-host
```

目标是让数据库客户端 span 使用连接目标的 hostname 作为 service name。

## 2. 问题现象

实际测试中发现以下问题：

1. 单节点 Redis 模式下，`split-by-host` 可以生效。
2. Lettuce 在 cluster 模式下，`split-by-host` 不生效。
3. Redisson 在现有实现下整体不生效。
4. APM 中部分 Redis span 的 `db_host` 为空，相关 `resource` 常见为 `EVALSHA`。

## 3. 源码结论

### 3.1 配置项本身没有问题

配置常量定义在：

- [dd-trace-api/src/main/java/datadog/trace/api/config/TraceInstrumentationConfig.java](/home/liurui/code/dd-trace-java/dd-trace-api/src/main/java/datadog/trace/api/config/TraceInstrumentationConfig.java:61)

加载配置的位置在：

- [internal-api/src/main/java/datadog/trace/api/Config.java](/home/liurui/code/dd-trace-java/internal-api/src/main/java/datadog/trace/api/Config.java:1753)

说明 `DD_TRACE_DB_CLIENT_SPLIT_BY_HOST` 的配置入口是正确的，不是参数名或配置映射问题。

### 3.2 `split-by-host` 生效的前提

真正按 host 改 service 的逻辑在：

- [dd-java-agent/agent-bootstrap/src/main/java/datadog/trace/bootstrap/instrumentation/decorator/DatabaseClientDecorator.java](/home/liurui/code/dd-trace-java/dd-java-agent/agent-bootstrap/src/main/java/datadog/trace/bootstrap/instrumentation/decorator/DatabaseClientDecorator.java:71)

核心逻辑是：

1. 先从连接对象中拿到 hostname。
2. 将 hostname 写入 `peer.hostname`。
3. 如果开启 `trace.db.client.split-by-host`，则将 span service name 改成该 hostname。

因此，只有在 instrumentation 能正确拿到 hostname 并走到 `onConnection()` 逻辑时，`split-by-host` 才会生效。

### 3.3 Redisson 不生效的根因

Redisson 旧实现的问题有两个：

1. `dbHostname()` 直接返回 `null`。
2. 调用链只走了 `onPeerConnection()`，没有走 `DatabaseClientDecorator.onConnection()`。

相关代码：

- [dd-java-agent/instrumentation/redisson/redisson-3.10.3/src/main/java/datadog/trace/instrumentation/redisson30/RedissonClientDecorator.java](/home/liurui/code/dd-trace-java/dd-java-agent/instrumentation/redisson/redisson-3.10.3/src/main/java/datadog/trace/instrumentation/redisson30/RedissonClientDecorator.java:62)
- [dd-java-agent/instrumentation/redisson/redisson-3.10.3/src/main/java/datadog/trace/instrumentation/redisson30/RedissonInstrumentation.java](/home/liurui/code/dd-trace-java/dd-java-agent/instrumentation/redisson/redisson-3.10.3/src/main/java/datadog/trace/instrumentation/redisson30/RedissonInstrumentation.java:74)

这意味着：

1. `peer.hostname` 不一定能稳定写入。
2. 即使有连接地址，也不会触发按 host 改 service name 的逻辑。

所以 Redisson “完全不生效” 是源码既有行为。

### 3.4 Lettuce cluster 不生效的根因

Lettuce 命令 span在结束前会尝试通过 `StatefulConnection -> RedisURI` 上下文取连接信息：

- [dd-java-agent/instrumentation/lettuce/lettuce-5.0/src/main/java/datadog/trace/instrumentation/lettuce5/LettuceAsyncCommandsAdvice.java](/home/liurui/code/dd-trace-java/dd-java-agent/instrumentation/lettuce/lettuce-5.0/src/main/java/datadog/trace/instrumentation/lettuce5/LettuceAsyncCommandsAdvice.java:39)

而这个上下文的来源是 `RedisClient` 的 connect 路径：

- [dd-java-agent/instrumentation/lettuce/lettuce-5.0/src/main/java/datadog/trace/instrumentation/lettuce5/LettuceClientInstrumentation.java](/home/liurui/code/dd-trace-java/dd-java-agent/instrumentation/lettuce/lettuce-5.0/src/main/java/datadog/trace/instrumentation/lettuce5/LettuceClientInstrumentation.java:26)

单节点模式通常能走这条路径，所以 `split-by-host` 正常。

cluster 模式不一定走 `RedisClient` 的这条 connect 链路，导致：

1. 命令 span 上拿不到 `RedisURI`。
2. `onConnection()` 不会执行。
3. `split-by-host` 无法按预期生效。

### 3.5 `EVALSHA` 与 `db_host` 为空的关系

`EVALSHA` 本身不是异常，通常代表 Redis 脚本缓存开启后，客户端发起的是脚本 SHA 调用，而不是直接 `EVAL`。

在 Redisson 测试中，测试代码显式关闭了 script cache，因此断言里常见的是 `EVAL`：

- [dd-java-agent/instrumentation/redisson/redisson-3.10.3/src/test/groovy/RedissonClientTest.groovy](/home/liurui/code/dd-trace-java/dd-java-agent/instrumentation/redisson/redisson-3.10.3/src/test/groovy/RedissonClientTest.groovy:39)
- [dd-java-agent/instrumentation/redisson/redisson-3.10.3/src/test/groovy/RedissonClientTest.groovy](/home/liurui/code/dd-trace-java/dd-java-agent/instrumentation/redisson/redisson-3.10.3/src/test/groovy/RedissonClientTest.groovy:194)

线上如果开启 script cache，看到 `EVALSHA` 是合理现象。

`db_host` 为空的根因仍然是 host 信息没有稳定写入 span，尤其在：

1. Redisson 旧逻辑未走 `onConnection()`。
2. `InetSocketAddress` 未解析时，旧的 `onPeerConnection()` 依赖 `remoteConnection.getAddress()`，可能拿不到 hostname。

相关逻辑见：

- [dd-java-agent/agent-bootstrap/src/main/java/datadog/trace/bootstrap/instrumentation/decorator/BaseDecorator.java](/home/liurui/code/dd-trace-java/dd-java-agent/agent-bootstrap/src/main/java/datadog/trace/bootstrap/instrumentation/decorator/BaseDecorator.java:155)

## 4. 已做调整

### 4.1 Redisson 调整

已对以下版本做了相同方向修复：

1. `redisson-2.0.0`
2. `redisson-2.3.0`
3. `redisson-3.10.3`

调整内容：

1. 新增基于 `InetSocketAddress` 的 `onConnection()` 处理。
2. 使用 `remoteConnection.getHostString()` 直接获取 host。
3. 将 host 回填到 `peer.hostname`。
4. 在开启 `trace.db.client.split-by-host` 时，将 service name 设置为 host。

示例代码位置：

- [dd-java-agent/instrumentation/redisson/redisson-3.10.3/src/main/java/datadog/trace/instrumentation/redisson30/RedissonClientDecorator.java](/home/liurui/code/dd-trace-java/dd-java-agent/instrumentation/redisson/redisson-3.10.3/src/main/java/datadog/trace/instrumentation/redisson30/RedissonClientDecorator.java:67)
- [dd-java-agent/instrumentation/redisson/redisson-3.10.3/src/main/java/datadog/trace/instrumentation/redisson30/RedissonInstrumentation.java](/home/liurui/code/dd-trace-java/dd-java-agent/instrumentation/redisson/redisson-3.10.3/src/main/java/datadog/trace/instrumentation/redisson30/RedissonInstrumentation.java:74)

### 4.2 Lettuce 调整

对 `lettuce-5.0` 的调整思路不是继续依赖 `RedisURI` 上下文，而是在实际 socket 写出时从 `channel.remoteAddress()` 读取真实目标地址。

调整内容：

1. 在 `DefaultEndpoint.write` 路径提取实际远端地址。
2. 将 host 写入 `peer.hostname`。
3. 将 port 写入 `peer.port`。
4. 若开启 `trace.db.client.split-by-host`，则直接按真实目标 host 更新 service name。

相关代码：

- [dd-java-agent/instrumentation/lettuce/lettuce-5.0/src/main/java/datadog/trace/instrumentation/lettuce5/LettuceDefaultEndpointAdvice.java](/home/liurui/code/dd-trace-java/dd-java-agent/instrumentation/lettuce/lettuce-5.0/src/main/java/datadog/trace/instrumentation/lettuce5/LettuceDefaultEndpointAdvice.java:17)

## 5. 调整后的预期效果

调整完成后，预期行为如下：

1. Redisson 不再是完全不生效。
2. Lettuce cluster 模式下，Redis span 可以基于真实连接节点进行 `split-by-host`。
3. `db_host` 为空的问题会明显收敛。
4. `EVALSHA` 类脚本命令也可以携带更完整的目标 host 信息。

## 6. 使用规范

为避免后续再次出现理解偏差，建议统一按以下规范使用。

### 6.1 配置规范

使用环境变量：

```text
DD_TRACE_DB_CLIENT_SPLIT_BY_HOST=true
```

或等价系统属性：

```text
-Ddd.trace.db.client.split-by-host=true
```

禁止混用非标准名称，避免出现“看起来像开了，实际没生效”的误判。

### 6.2 命名规范

`split-by-host` 的最终 service 名，本质上取决于客户端实际感知到的 host。

因此如果希望 APM 展示为业务可读名称，例如：

1. `mysql-1`
2. `redis-order-1`
3. `redis-member-cluster-a`

则应用配置中的连接地址、DNS、CNAME 或服务发现返回值，最终也需要体现该命名。

如果客户端最终连到的是：

1. 真实 IP
2. 节点域名
3. cluster 节点地址

那么 APM 中拆分后的 service 也通常会是这些值，而不是业务别名。

### 6.3 适用范围规范

`split-by-host` 更适合以下场景：

1. 同一应用需要区分多个数据库或多个 Redis 集群。
2. 各业务集群已经有稳定的 hostname 命名规范。
3. 希望 service 维度直接按目标节点或目标集群拆分。

不适合直接拿来做“业务语义映射”的场景，除非基础连接地址本身已经完成别名化。

### 6.4 排查规范

遇到“配置已开启但不生效”时，建议按以下顺序排查：

1. 确认 span 上是否存在 `peer.hostname`。
2. 确认客户端类型与模式，是单节点、哨兵、主从还是 cluster。
3. 确认连接地址最终暴露的是业务别名、节点域名还是 IP。
4. 确认 instrumentation 是否覆盖到实际调用路径。
5. 对脚本命令区分 `EVAL` 与 `EVALSHA`，不要把 `EVALSHA` 误判为异常。

## 7. 验证情况

已做编译验证，以下编译通过：

```bash
./gradlew :dd-java-agent:instrumentation:lettuce:lettuce-5.0:compileJava :dd-java-agent:instrumentation:redisson:redisson-3.10.3:compileJava
```

此前对三个 `redisson` 模块的编译也已通过。

`spotlessJavaCheck` 未全绿，但失败点在 `lettuce` 模块中原有文件的格式问题，不属于本次功能逻辑失败。

## 8. 后续建议

建议后续按以下方向继续补强：

1. 为 `Redisson` 增加覆盖 `split-by-host` 的回归测试。
2. 为 `Lettuce cluster` 增加 cluster 模式测试用例，避免后续回退。
3. 如客户要求展示固定业务别名，建议结合 DNS/CNAME 或连接配置治理，而不是仅依赖 agent 侧自动推断。
4. 若后续还涉及 MySQL、Kafka、RocketMQ 等中间件，建议统一沉淀一份“service 命名规范”，避免不同组件各自为政。

## 9. 一句话结论

这次问题不是配置项错误，而是 Redis 不同客户端在 instrumentation 层对 hostname 的提取路径不一致。

其中：

1. Redisson 旧实现本身就没有正确支持 `split-by-host`。
2. Lettuce cluster 模式原先没有走到能拿 `RedisURI` 的单节点链路。

本次调整后，Redis span 会更稳定地携带真实 host，并在开启 `split-by-host` 时按 host 生成 service name。
