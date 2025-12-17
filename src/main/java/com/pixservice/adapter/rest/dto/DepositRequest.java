package com.pixservice.adapter.rest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO para depósito
 * 
 * @param amount Valor a ser depositado (deve ser maior que zero)
 */
public record DepositRequest(
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    BigDecimal amount
) {}
