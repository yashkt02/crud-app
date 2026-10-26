package com.example.expensetracker.service;

import com.example.expensetracker.model.ExpenseEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ExpenseEventPublisher {
    public static final String TOPIC = "expense-events";

    private final KafkaTemplate<String, ExpenseEvent> kafkaTemplate;
    private final boolean enabled;

    public ExpenseEventPublisher(KafkaTemplate<String, ExpenseEvent> kafkaTemplate,
                                 @Value("${app.kafka.producer.enabled:true}") boolean enabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.enabled = enabled;
    }

    public void publish(ExpenseEvent event) {
        if (enabled) {
            kafkaTemplate.send(TOPIC, String.valueOf(event.id()), event);
        }
    }
}