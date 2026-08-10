package com.bankingapp.transaction_service.event;

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
    private String transactionType;      // DEPOSIT, WITHDRAWAL, TRANSFER
    private String accountNumber;        // the account to notify about
    private BigDecimal amount;
    private String status;               // SUCCESS or FAILED
    private LocalDateTime timestamp;
}