# Redis `split-by-host` 问题汇报说明

## 1. 背景

客户希望通过 `DD_TRACE_DB_CLIENT_SPLIT_BY_HOST=true`，将 APM 中默认的数据库或中间件 service 名按目标集群或节点区分开，例如：

1. `mysql` 展示为 `mysql-1`
2. `redis` 展示为 `redis-order-1`

这样可以在 APM 中区分不同业务、不同集群或不同节点的访问流量。

## 2. 问题现象

实际核查与测试结果如下：

1. Redis 单节点模式下，`split-by-host` 可以正常生效。
2. Lettuce 在 cluster 模式下，`split-by-host` 不生效。
3. Redisson 在现有实现下基本不生效。
4. APM 中部分 Redis span 的 `db_host` 为空，相关 `resource` 常见为 `EVALSHA`。

## 3. 核查结论

### 3.1 配置项没有问题

`DD_TRACE_DB_CLIENT_SPLIT_BY_HOST` 对应内部配置 `trace.db.client.split-by-host`，参数本身正确。

因此，这次问题不是配置写错，而是不同客户端在采集 hostname 的实现路径不一致。

### 3.2 Redisson 不生效的原因

Redisson 旧实现里没有完整走到按 host 改 service 的逻辑，导致：

1. `peer.hostname` 不能稳定写入。
2. `split-by-host` 无法按预期把 service name 改成目标 host。

所以 Redisson “完全不生效” 属于实现层问题，不是使用问题。

### 3.3 Lettuce cluster 不生效的原因

Lettuce 单节点模式下可以拿到连接信息，因此 `split-by-host` 正常。

但在 cluster 模式下，原先的实现路径拿不到用于改 service 的连接 host，导致：

1. 命令 span 上缺少可用的 hostname。
2. `split-by-host` 在 cluster 模式下失效。

### 3.4 `EVALSHA` 与 `db_host` 为空的说明

`EVALSHA` 本身是正常现象，通常表示 Redis 脚本缓存开启后，客户端执行的是脚本 SHA 调用。

`db_host` 为空的根因仍然是 host 信息没有被稳定写入 span，不代表 `EVALSHA` 本身异常。

## 4. 已做调整

本次已完成以下调整：

1. 修复 `Redisson 2.0.0 / 2.3.0 / 3.10.3` 的 host 提取逻辑。
2. 在 Redisson 上补齐按 host 回填 `peer.hostname` 并在开启 `split-by-host` 时更新 service name 的能力。
3. 调整 `Lettuce 5` 的 Redis 目标地址获取方式，在实际网络写出时读取真实连接节点地址。
4. 让 `Lettuce cluster` 模式下的 Redis span 能基于真实节点 host 参与 `split-by-host`。

## 5. 调整后的预期效果

调整后预期行为如下：

1. Redisson 不再是完全不生效。
2. Lettuce cluster 模式下可以按真实 Redis 节点拆分 service。
3. Redis span 中 `db_host` 为空的问题会明显收敛。
4. 包括 `EVALSHA` 在内的脚本类 Redis 命令，也能更稳定携带目标 host 信息。

## 6. 使用规范

### 6.1 配置规范

统一使用：

```text
DD_TRACE_DB_CLIENT_SPLIT_BY_HOST=true
```

或等价系统属性：

```text
-Ddd.trace.db.client.split-by-host=true
```

避免使用非标准或自定义名称，防止误判为“配置已开启但功能未生效”。

### 6.2 命名规范

`split-by-host` 最终生成的 service name，本质上取决于客户端实际感知到的 host。

也就是说，如果应用实际连接的是：

1. IP
2. 节点域名
3. cluster 节点地址

那么 APM 上最终拆分出来的 service 通常也会是这些值。

如果希望 APM 中稳定展示为业务别名，例如：

1. `mysql-1`
2. `mysql-order-cluster`
3. `redis-order-1`
4. `redis-member-cluster-a`

则必须保证应用侧实际使用的连接地址、DNS、CNAME 或服务发现返回值本身就是这些别名。

### 6.3 MySQL 集群别名规范

这一点需要特别补充说明，作为后续用户使用规范：

1. 如果客户希望在 APM 中看到 `mysql-1`、`mysql-2`、`mysql-order-cluster` 这样的名称，不能只依赖 `split-by-host` 参数本身。
2. `split-by-host` 只负责“按实际 host 拆分 service”，不会把 `mysql` 自动翻译成业务别名。
3. 因此 MySQL 集群必须在连接配置层就使用统一别名，例如：
   `mysql-1.prod.company.internal`
   `mysql-order-cluster.prod.company.internal`
4. 建议客户统一使用集群别名或业务别名访问 MySQL，而不是直接使用裸 IP 或随机节点名。
5. 只有这样，`split-by-host` 在 APM 中展示出来的 service 名才会稳定、可读、可治理。

换句话说：

如果 MySQL 希望展示成 `mysql-1`，那应用实际连的 host 也应该是 `mysql-1` 或以其为核心的别名，而不是某个临时 IP。

### 6.4 适用范围规范

`split-by-host` 更适合以下场景：

1. 一个应用会访问多个 MySQL 集群或多个 Redis 集群。
2. 客户已有稳定的域名和别名治理规范。
3. 希望 APM 在 service 维度上直接区分目标集群或节点。

如果客户没有统一的连接命名规范，而是直接连接 IP，那么最终 APM 展示也会偏底层、不可读。

## 7. 验证情况

本次变更已完成相关模块编译验证，Redis 相关改动编译通过。

当前未发现与本次逻辑调整直接相关的编译问题。

## 8. 建议

建议从两条线同时推进：

1. 代码层面：继续补充 Redis cluster 和 Redisson 的回归测试，防止后续回退。
2. 使用层面：推动客户统一制定数据库和中间件连接命名规范。

其中命名规范建议明确要求：

1. MySQL 集群统一使用业务别名或集群别名访问，例如 `mysql-1`、`mysql-order-cluster`。
2. Redis 集群统一使用业务别名或集群别名访问，例如 `redis-order-1`、`redis-member-cluster-a`。
3. 禁止在生产配置中直接使用裸 IP 作为长期连接地址。
4. APM 命名规范应与 DNS、CNAME、服务发现命名保持一致。

## 9. 汇总结论

本次问题的本质不是配置项错误，而是 Redis 不同客户端在 hostname 提取路径上存在实现差异。

调整后：

1. Redisson 已补齐 `split-by-host` 所需的 host 写入和 service 更新逻辑。
2. Lettuce cluster 已改为基于真实连接节点地址参与 `split-by-host`。
3. Redis span 的 host 信息完整性会明显提升。

同时需要明确规范：

`split-by-host` 只能按“应用实际连接到的 host”来拆分 service，不能代替命名治理。

特别是 MySQL 场景，如果客户希望最终在 APM 中稳定展示为 `mysql-1` 这类集群别名，那么应用连接配置、DNS 或服务发现层必须优先完成别名规范化，这样才能保证用户使用方式统一、APM 展示结果稳定。
