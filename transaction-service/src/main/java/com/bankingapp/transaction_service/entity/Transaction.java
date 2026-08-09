package com.bankingapp.transaction_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String type; // DEPOSIT, WITHDRAWAL, TRANSFER

    @Column
    private String fromAccountNumber; // null for DEPOSIT

    @Column
    private String toAccountNumber; // null for WITHDRAWAL

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Builder.Default
    @Column(nullable = false)
    private String status = "SUCCESS"; // SUCCESS, FAILED

    @Column
    private String failureReason;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}