package com.clearing.book;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clearing.common.Channels;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration
public class QueueConfig {

    @Bean
    public Queue screenDlq() {
        return QueueBuilder.durable(Channels.SCREEN_DLQ).build();
    }

    @Bean
    @DependsOn("screenDlq")
    public Queue screenQueue() {
        return QueueBuilder.durable(Channels.SCREEN)
                .deadLetterExchange("")
                .deadLetterRoutingKey(Channels.SCREEN_DLQ)
                .build();
    }

    @Bean
    public Queue settleDlq() {
        return QueueBuilder.durable(Channels.SETTLE_DLQ).build();
    }

    @Bean
    @DependsOn("settleDlq")
    public Queue settleQueue() {
        return QueueBuilder.durable(Channels.SETTLE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(Channels.SETTLE_DLQ)
                .build();
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("*");
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
