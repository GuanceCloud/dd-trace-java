# Redis 配置地址命名与真实节点地址

`DD_TRACE_PEER_HOSTNAME_FROM_CONFIG_ENABLED` 默认关闭。开启后，Redis span 的
`peer.hostname` 可以使用应用配置中的 host；同时开启
`DD_TRACE_DB_CLIENT_SPLIT_BY_HOST=true`，service 将按这个 host 拆分。
已有的 `DD_SERVICE_MAPPING` 可以继续把配置域名映射为集群别名。

```shell
DD_TRACE_PEER_HOSTNAME_FROM_CONFIG_ENABLED=true
DD_TRACE_DB_CLIENT_SPLIT_BY_HOST=true
DD_SERVICE_MAPPING=redis.example.com:ali-redis-uat
```

## Redisson

Redisson 3.10.3+ 的节点 client 创建路径会读取所属连接管理器的原始配置，而不是把
`CLUSTER SLOTS` 返回的节点 IP 当作应用配置。拓扑刷新、节点新增及重连后创建的 client
同样经过这个入口；同一 JVM 中的不同 Redisson 实例各自使用自己的配置。

开启开关后的规则：

- Cluster：使用配置列表中第一个可解析的 seed host。
- Replicated：使用配置列表中第一个可解析的节点 host。
- Sentinel：使用配置列表中第一个可解析的 Sentinel host。
- Single、显式 Master/Slave 和直接创建的 `RedisClient`：使用 client 自己的配置 host。
- 没有可用配置 host 时，保留连接地址命名。

因此，多个 seed 代表一个逻辑服务时应保持配置顺序一致；使用同一 Sentinel 集群的多个
master 会按同一 Sentinel host 命名。配置本身是 IP 时不会自动推导出业务别名。

仅提取 host，不把密码、用户名、完整 URI 放入标签。实际节点地址继续写入
`peer.ipv4` 或 `peer.ipv6`，实际连接端口写入 `peer.port`。
关闭开关时不改变现有命名行为。

地址读取通过缓存的方法句柄兼容 `RedisClientConfig.getAddress()` 的 `URI` /
`RedisURI` 返回类型，避免该签名变化导致地址采集 instrumentation 被 muzzle 排除。
3.11.6 已经使用 `RedisURI`，不能用作旧 `URI` 接口的对照版本；旧接口测试使用 3.10.3。
配置解析只发生在 client 创建时，不在每条 Redis 命令上执行。

## Lettuce

Lettuce 5+ 的集群连接显式使用 `RedisClusterClient` 的初始 seed URI 作为命名来源，
防止节点连接参数中的 IP 取代集群 host；业务命令和拓扑刷新的连接走同一套命名规则。
该行为只在开关开启时启用。底层 `DefaultEndpoint.write` 不再覆盖已经设置的配置 host，
避免异步写入在命令 span 完成装饰后又把名称改回节点 IP；尚无 hostname 的 span 仍可
回退到 socket host。回归测试直接通过真实 endpoint 发送命令，覆盖这种装饰与写入顺序。

连接完成后，从对应 Netty channel 读取实际 socket 地址。`ConnectionFuture` 保存的地址
可能尚未解析，此时仅调用 `InetSocketAddress.getAddress()` 拿不到 IP；本地集群回归测试
已观察到这种情况下辅助命令仍正确命名为 seed，但缺少 `peer.ipv4`。本次同时修复此项，
不通过额外 DNS 查询推测真实 IP。地址读取发生在连接完成时，命令路径不增加反射。

同时补齐 Master/Replica instrumentation 的辅助类清单，避免共享 decorator 引用的
`LettuceConnectionInfo` 未声明时被 muzzle 排除。

## DataKit 字段提取

DataKit 的 ddtrace input 会将默认映射和 `customer_tags` 中的 span meta 提取为可查询字段，
并把字段名中的 `.` 改成 `_`。当前本地 DataKit 源码的默认映射不包含
`peer.ipv4` / `peer.ipv6`。在现有 `[[inputs.ddtrace]]` 中，把以下项追加到
`customer_tags`（保留原有项）：

```toml
customer_tags = ["peer.hostname", "peer.ipv4", "peer.ipv6"]
```

容器环境也可以通过 `ENV_INPUT_DDTRACE_CUSTOMER_TAGS` 配置同一 JSON 数组。
对应一级字段为 `peer_hostname`、`peer_ipv4`、`peer_ipv6`。
`peer.port` 在 Java agent 中是数值标签，会写入协议的 `metrics`，
不能通过只处理 `meta` 的 `customer_tags` 提取；保留 `message` 时可在其 `metrics` 中查看。
无需为这次 agent 修复同步修改 DataKit 源码；若希望所有部署默认提取，则可另行扩展
DataKit 的 `internal/plugins/inputs/ddtrace/tags.go` 默认映射。

未提取的字段通常仍保留在 `message` 的 `meta` 中；若配置 `del_message = true`，
这部分不会保留。字段缺失不能单独证明 agent 没有发送真实 IP。
上述结论依据本地 DataKit 的 `tags.go`、`input.go`、`env.go`、`ddtrace_http.go`
和 `internal/trace/customtags.go`，不代表已经验证目标部署环境的实际配置或数据。

## 本地验证

使用真实 Redis 6.2.6 单节点 cluster（全 16384 个 slot），seed 为 `localhost`，
拓扑公告地址为 `127.0.0.1`；两者刻意不同，以验证节点发现后仍能保留配置来源。
开关开启/关闭分别在独立 JVM 中执行，均开启 split-by-host。

Redisson 3.10.3、3.11.6、3.27.2、3.52.0 已通过新增回归：普通 SET/GET、
批量 SET/GET、直接创建 RedisClient、同节点上的两个独立客户端，以及 hostname、
service、实际 IP 和端口断言。配置解析单元测试另覆盖 Sentinel、Replicated、IPv6 URI、
认证信息和缺失配置；没有运行 Sentinel/Replicated 的真实拓扑测试。

Lettuce 5.0.0.RELEASE、6.2.0.RELEASE、7.6.0.RELEASE 已通过拓扑刷新、节点业务命令、
CLUSTER 命令及底层 endpoint 直接写入的开关对照回归。
6.2 的开启开关用例在修复 endpoint 覆盖前失败（`localhost` 被改成 `127.0.0.1`），
修复后通过；关闭开关用例在修复前后均通过。

本次使用 JDK 25 执行，以下测试合计 346 项，零失败、零跳过：

| 客户端版本 | 常规/配置解析测试 | 独立 JVM 测试 |
| --- | ---: | ---: |
| Redisson 3.10.3 | 21 | 24 |
| Redisson 3.11.6 | 5 | 8 |
| Redisson 3.27.2 | 5 | 8 |
| Redisson 3.52.0 | 21 | 24 |
| Lettuce 5.0.0.RELEASE | 38 | 75 |
| Lettuce 6.2.0.RELEASE | — | 4 |
| Lettuce 7.6.0.RELEASE | 38 | 75 |

上述七个客户端版本的定向 muzzle 正向检查全部通过，Redisson 3.10.2 的负向检查通过，
确认没有扩大到 3.10.3 之前的版本。未执行整个支持区间的全版本 muzzle 扫描。
补齐 Master/Replica 辅助类声明后，另行重跑 Lettuce 5.0 和 7.6 的既有
`Lettuce5MasterReplicaTest`，两者均通过。改动文件的 Spotless 检查、JSON 解析和
`git diff --check` 通过；新增测试配置的依赖锁已生成，原有配置的依赖版本保持不变。

这是本地测试结果，尚未在客户环境验证，也未执行 native-image 构建。

复现和回归入口：

```shell
./gradlew :dd-java-agent:instrumentation:redisson:redisson-3.10.3:forkedTest --tests 'RedissonCluster*ForkedTest'
./gradlew :dd-java-agent:instrumentation:redisson:redisson-3.10.3:redisson311ForkedTest --tests 'RedissonCluster*ForkedTest'
./gradlew :dd-java-agent:instrumentation:redisson:redisson-3.10.3:redisson327ForkedTest --tests 'RedissonCluster*ForkedTest'
./gradlew :dd-java-agent:instrumentation:redisson:redisson-3.10.3:latestDepForkedTest --tests 'RedissonCluster*ForkedTest'
./gradlew :dd-java-agent:instrumentation:lettuce:lettuce-5.0:forkedTest --tests 'LettuceCluster*ForkedTest'
./gradlew :dd-java-agent:instrumentation:lettuce:lettuce-5.0:lettuce62ForkedTest --tests 'LettuceCluster*ForkedTest'
./gradlew :dd-java-agent:instrumentation:lettuce:lettuce-5.0:latestDepForkedTest --tests 'LettuceCluster*ForkedTest'
```
