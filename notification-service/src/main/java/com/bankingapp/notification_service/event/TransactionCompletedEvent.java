package com.bankingapp.notification_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionCompletedEvent {
    private String transactionType;
    private String accountNumber;
    private BigDecimal amount;
    private String status;
    private LocalDateTime timestamp;
}