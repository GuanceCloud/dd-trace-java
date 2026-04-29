# Kafka / RabbitMQ 业务区分方案汇报说明

## 1. 背景

客户希望在 APM 中对 Kafka 和 RabbitMQ 的访问进行业务区分。

当前设想是通过 pipeline 提取以下字段中的 hostname，再映射成业务名称：

1. `peer.service`
2. `messaging.kafka.bootstrap.servers`

但实际观察到，部分 operation 不会出现这些字段，导致无法稳定按 hostname 做业务归类。

## 2. 问题现象

客户反馈以下 operation 中缺少 hostname 相关字段：

### 2.1 Kafka

1. `spring.consume`
2. `http.request`
3. `okhttp.request`
4. `MessageTemplateServiceImpl.doNoticeKafka`
5. `MessageTemplateServiceImpl.kafkaDoNotice`
6. `MessageTemplateServiceImpl.kafkaDoNotices`
7. `LarkAlarmStrategy.larkAlarm`

### 2.2 RabbitMQ

1. `amqp.consume`
2. `spring.consume`

这些 span 中常见情况包括：

1. 没有 `peer.service`
2. 没有 `messaging.kafka.bootstrap.servers`
3. 没有其他可直接代表 broker hostname 的字段

## 3. 核查结论

### 3.1 这不是单纯的字段遗漏，而是埋点模型边界

当前 Java Agent 的实现里，不是所有和消息链路相关的 span 都会带 broker hostname。

原因是不同 operation 对应的是不同类型的 span：

1. 有些是 Kafka / RabbitMQ 客户端 span
2. 有些是 Spring 框架消费 span
3. 有些是 HTTP 客户端 span
4. 有些是业务方法 span

只有真正的 broker client span，才更有机会携带目标集群地址。

### 3.2 `peer.service` 不会覆盖所有消费场景

`peer.service` 默认只会在 `span.kind=client` 或 `span.kind=producer` 的 span 上计算。

因此：

1. `spring.consume`
2. `amqp.consume`

这类 consumer span 本身就不一定会有 `peer.service`。

### 3.3 Kafka 的 `messaging.kafka.bootstrap.servers` 也不是全量字段

Kafka 埋点里，`messaging.kafka.bootstrap.servers` 只会在真正的 Kafka produce / consume span 上尝试写入。

它不会天然出现在以下 span 上：

1. `http.request`
2. `okhttp.request`
3. 自定义业务方法 span
4. 仅用于框架层包裹的消费 span

另外，Kafka 消费侧补充 `bootstrap.servers` 的实现还依赖对应 consumer 上下文链路，不能把它当成所有 Kafka 相关 span 都一定存在的字段。

### 3.4 RabbitMQ 的消费 span 当前重点是 queue，而不是 hostname

RabbitMQ 的 Spring 消费 span 当前主要记录的是消费 queue 对应的 resource。

因此：

1. `amqp.consume`
2. `spring.consume`

更适合按 queue 识别业务，而不是期待稳定拿到 broker hostname。

## 4. 对客户当前方案的判断

如果目标是“区分不同集群”，用 hostname 做 mapping 是可以成立的。

如果目标是“区分不同业务”，只依赖 hostname 并不稳妥，原因如下：

1. 同一个 Kafka 集群本来就可能承载多个业务 topic。
2. 同一个 RabbitMQ 集群本来就可能承载多个业务 queue。
3. 很多业务相关 span 根本不是 broker client span，不会带 hostname。

因此，hostname 更适合表达“基础设施集群”，不适合单独作为“业务归属”的主维度。

## 5. 推荐方案

建议将识别规则拆成两层：

### 5.1 集群层

用于区分访问的是哪个 Kafka / RabbitMQ 集群。

推荐字段：

1. Kafka：`messaging.kafka.bootstrap.servers`
2. RabbitMQ：`peer.hostname` / `peer.service`

适用范围：

1. 真正的 Kafka produce / consume span
2. 真正的 RabbitMQ client / producer span

映射结果示例：

1. `kafka-prod-01.company.internal` -> `kafka-1`
2. `mq-order.company.internal` -> `rabbitmq-order`

### 5.2 业务层

用于区分具体是哪条业务链路。

推荐字段：

1. Kafka：`topic`
2. Kafka：`consumer.group`
3. RabbitMQ：`queue`
4. RabbitMQ：`exchange`
5. RabbitMQ：`routing_key`

映射结果示例：

1. `notice-topic` -> `notice`
2. `alarm-topic` -> `alarm`
3. `order.delay.queue` -> `order`
4. `member.sync.queue` -> `member`

## 6. 规范建议

### 6.1 Kafka 规范

如果客户要区分不同业务，建议优先使用以下规则：

1. `topic -> 业务名称`
2. `topic + consumer.group -> 业务名称`

不建议只用 `bootstrap.servers` 去表达业务，因为它更适合表达集群。

如果需要同时区分“集群”和“业务”，建议：

1. `messaging.kafka.bootstrap.servers -> kafka-1`
2. `topic -> order / notice / alarm`

也就是说：

1. 集群别名由 `bootstrap.servers` 决定
2. 业务别名由 `topic` 或 `consumer.group` 决定

### 6.2 RabbitMQ 规范

如果客户要区分不同业务，建议优先使用以下规则：

1. `queue -> 业务名称`
2. `exchange + routing_key -> 业务名称`

不建议只用 hostname 去表达业务，因为一个 MQ 集群通常同时承载多个业务 queue。

如果需要同时区分“集群”和“业务”，建议：

1. `peer.hostname -> rabbitmq-1`
2. `queue -> order / notice / alarm`

### 6.3 对缺失 hostname 的 operation 的处理规范

以下 operation 不应继续强依赖 hostname 做业务区分：

1. `spring.consume`
2. `amqp.consume`
3. `http.request`
4. `okhttp.request`
5. 业务方法 span，例如 `MessageTemplateServiceImpl.*`

这些 span 应采用以下方式处理：

1. 如果 span 本身有 topic / queue 等业务字段，则直接做业务 mapping
2. 如果 span 本身没有消息维度字段，则只能通过上游链路字段、业务代码手动打 tag，或 pipeline 上下文归并来补齐

## 7. 可直接利用的现有能力

当前已有按 destination 拆分 service 的能力，可作为业务区分的基础：

1. Kafka 可按 topic 做 split
2. RabbitMQ 可按 queue 做 split

这类能力比按 hostname 拆分更贴近消息中间件的业务使用模式。

因此，对于 Kafka / RabbitMQ：

1. 如果想区分“集群”，看 hostname / bootstrap servers
2. 如果想区分“业务”，优先看 topic / queue / exchange / routing key

## 8. 最终建议

建议后续统一采用以下规范：

1. 不把 hostname 作为 Kafka / RabbitMQ 业务归类的唯一依据
2. hostname 只负责标识基础设施集群
3. Kafka 业务统一由 `topic` 或 `topic + consumer.group` 归类
4. RabbitMQ 业务统一由 `queue` 或 `exchange + routing_key` 归类
5. 对于 `spring.consume`、`amqp.consume`、业务方法 span、HTTP span，不再要求必须出现 hostname
6. 如果客户希望这些上层 span 也展示业务名称，需要通过业务代码补 tag 或 pipeline 规则做二次归并

## 9. 汇总结论

本次问题的核心不是“某些字段采集失败”，而是不同 operation 对应的 span 类型不同，天然就不共享同一套 hostname 字段。

因此：

1. `peer.service` 和 `messaging.kafka.bootstrap.servers` 只能用于部分 broker span
2. 不能要求所有 Kafka / RabbitMQ 相关 span 都带 hostname
3. 如果客户目标是区分不同业务，应该把 topic / queue 作为主维度
4. 如果客户目标是区分不同集群，再额外用 hostname / bootstrap servers 作为集群维度补充

最稳妥的落地方式是两层 mapping：

1. 集群层：hostname / bootstrap servers -> 集群别名
2. 业务层：topic / queue / exchange / routing key -> 业务别名

这样既符合当前 agent 的埋点模型，也更容易形成长期稳定的使用规范。
