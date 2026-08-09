package com.bankingapp.transaction_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AmountRequest {
    @NotBlank
    private String accountNumber;

    @NotNull
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;
}
