package org.kovalenko.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    // ===== Exchanges =====

    @Bean
    public DirectExchange analyzeExchange() {
        return new DirectExchange(RabbitQueues.ANALYZE_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange resultsExchange() {
        return new DirectExchange(RabbitQueues.RESULTS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(RabbitQueues.DLX_EXCHANGE, true, false);
    }

    // ===== Queues =====

    @Bean
    public Queue analyzeQueue() {
        return QueueBuilder.durable(RabbitQueues.ANALYZE_QUEUE)
                .withArgument("x-dead-letter-exchange", RabbitQueues.DLX_EXCHANGE)
                .build();
    }

    @Bean
    public Queue resultsQueue() {
        return QueueBuilder.durable(RabbitQueues.RESULTS_QUEUE)
                .withArgument("x-dead-letter-exchange", RabbitQueues.DLX_EXCHANGE)
                .build();
    }

    @Bean
    public Queue dlqQueue() {
        return QueueBuilder.durable(RabbitQueues.DLQ_QUEUE).build();
    }

    // ===== Bindings =====

    @Bean
    public Binding analyzeBinding(Queue analyzeQueue, DirectExchange analyzeExchange) {
        return BindingBuilder.bind(analyzeQueue)
                .to(analyzeExchange)
                .with(RabbitQueues.ANALYZE_ROUTING_KEY);
    }

    @Bean
    public Binding resultsBinding(Queue resultsQueue, DirectExchange resultsExchange) {
        return BindingBuilder.bind(resultsQueue)
                .to(resultsExchange)
                .with(RabbitQueues.RESULTS_ROUTING_KEY);
    }

    @Bean
    public Binding dlqBinding(Queue dlqQueue, DirectExchange dlxExchange) {
        // catch-all: any message routed to DLX lands in DLQ
        return BindingBuilder.bind(dlqQueue).to(dlxExchange).with("#");
    }

    // ===== Serialization =====

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}