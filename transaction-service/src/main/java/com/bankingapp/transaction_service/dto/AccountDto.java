package com.bankingapp.transaction_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountDto {
    private Long id;
    private String accountNumber;
    private String ownerUsername;
    private String accountType;
    private BigDecimal balance;
    private String status;
}