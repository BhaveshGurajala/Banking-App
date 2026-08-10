package com.bankingapp.transaction_service.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventPublisher {

    private static final String TOPIC = "transaction-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(TransactionCompletedEvent event) {
        kafkaTemplate.send(TOPIC, event.getAccountNumber(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish transaction event for account {}: {}",
                                event.getAccountNumber(), ex.getMessage());
                    } else {
                        log.info("Published transaction event for account {}", event.getAccountNumber());
                    }
                });
    }
}