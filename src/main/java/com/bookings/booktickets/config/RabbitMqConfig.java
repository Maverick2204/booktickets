package com.bookings.booktickets.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String BOOKING_EXCHANGE = "booking.exchange";
    public static final String BOOKING_CREATED_ROUTING_KEY = "booking.created";
    public static final String CONFIRMATION_QUEUE = "booking.confirmation.queue";

    public static final String DLX_EXCHANGE = "booking.dlx";
    public static final String DLQ_ROUTING_KEY = "booking.confirmation.dlq";
    public static final String CONFIRMATION_DLQ = "booking.confirmation.dlq";

    public static final String RETRY_COUNT_HEADER = "x-retry-count";

    @Bean
    public TopicExchange bookingExchange() {
        return new TopicExchange(BOOKING_EXCHANGE);
    }

    @Bean
    public TopicExchange bookingDlx() {
        return new TopicExchange(DLX_EXCHANGE);
    }

    @Bean
    public Queue confirmationQueue() {
        return QueueBuilder.durable(CONFIRMATION_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue confirmationDlq() {
        return QueueBuilder.durable(CONFIRMATION_DLQ).build();
    }

    @Bean
    public Binding confirmationBinding(Queue confirmationQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(confirmationQueue).to(bookingExchange).with(BOOKING_CREATED_ROUTING_KEY);
    }

    @Bean
    public Binding confirmationDlqBinding(Queue confirmationDlq, TopicExchange bookingDlx) {
        return BindingBuilder.bind(confirmationDlq).to(bookingDlx).with(DLQ_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
