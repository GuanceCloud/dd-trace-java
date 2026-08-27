package rabbitbatch;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class BatchRabbitMQApplication {

  public static String hostName = "127.0.0.1";
  public static int port = 5672;

  static final String topicExchangeName = "batch-exchange";

  static final String queueName = "batch-queue";

  @Bean
  Queue queue() {
    return new Queue(queueName, false);
  }

  @Bean
  TopicExchange exchange() {
    return new TopicExchange(topicExchangeName);
  }

  @Bean
  Binding binding(Queue queue, TopicExchange exchange) {
    return BindingBuilder.bind(queue).to(exchange).with("batch.#");
  }

  @Bean
  ConnectionFactory connectionFactory() {
    return new CachingConnectionFactory(hostName, port);
  }

  @Bean
  BatchReceiver batchReceiver() {
    return new BatchReceiver();
  }

  @Bean
  SimpleMessageListenerContainer batchContainer(
      ConnectionFactory connectionFactory, BatchReceiver batchReceiver) {
    SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.setQueueNames(queueName);
    container.setMessageListener(batchReceiver);
    container.setConsumerBatchEnabled(true);
    container.setBatchSize(3);
    container.setReceiveTimeout(1000);
    return container;
  }

  public static ConfigurableApplicationContext run() {
    return SpringApplication.run(BatchRabbitMQApplication.class);
  }
}
