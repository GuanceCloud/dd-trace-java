# Kafka / RabbitMQ 客户回复话术版

可以确认，这部分不是单纯的字段采集异常，而是当前埋点模型本身的边界。

`peer.service` 默认只会在 `client` 或 `producer` 类型的 span 上计算，因此像 `spring.consume`、`amqp.consume` 这类消费 span，本身就不一定会有 `peer.service`。  
同样，`messaging.kafka.bootstrap.servers` 也只会出现在真正的 Kafka produce / consume span 上，不会天然出现在 `http.request`、`okhttp.request`、业务方法 span 或部分框架包装 span 上。

因此，如果目标是“区分不同业务”，不建议继续把 hostname 作为唯一依据。  
对于 Kafka，更推荐按 `topic` 或 `topic + consumer.group` 做业务映射；对于 RabbitMQ，更推荐按 `queue` 或 `exchange + routing_key` 做业务映射。

如果同时还需要区分“不同集群”，建议拆成两层：

1. 集群层：  
   Kafka 用 `messaging.kafka.bootstrap.servers`，RabbitMQ 用 `peer.hostname` / `peer.service`，映射成 `kafka-1`、`rabbitmq-1` 这类集群别名。

2. 业务层：  
   Kafka 用 `topic` / `consumer.group`，RabbitMQ 用 `queue` / `exchange + routing_key`，映射成 `order`、`notice`、`alarm` 等业务名称。

也就是说：

1. hostname 更适合表达“访问的是哪个集群”
2. topic / queue 更适合表达“这是哪个业务”

如果客户希望像 `spring.consume`、`amqp.consume`、业务方法 span 这类上层 span 也稳定显示业务名称，那就需要通过业务代码补充自定义 tag，或者在 pipeline 中结合 topic / queue 等字段做二次归类，不能单纯依赖 hostname。
