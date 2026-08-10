package com.bankingapp.notification_service.listener;

import com.bankingapp.notification_service.event.TransactionCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TransactionEventListener {

    @KafkaListener(topics = "transaction-events", groupId = "notification-group")
    public void handleTransactionEvent(TransactionCompletedEvent event) {
        String message = buildNotificationMessage(event);
        log.info("NOTIFICATION -> account {}: {}", event.getAccountNumber(), message);
    }

    private String buildNotificationMessage(TransactionCompletedEvent event) {
        return switch (event.getTransactionType()) {
            case "DEPOSIT" -> "Your account was credited with ₹" + event.getAmount();
            case "WITHDRAWAL" -> "₹" + event.getAmount() + " was withdrawn from your account";
            case "TRANSFER_OUT" -> "₹" + event.getAmount() + " was sent from your account";
            case "TRANSFER_IN" -> "₹" + event.getAmount() + " was received in your account";
            default -> "A transaction occurred on your account";
        };
    }
}