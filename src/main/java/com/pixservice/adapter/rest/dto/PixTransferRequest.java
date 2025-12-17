package com.pixservice.adapter.rest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO para transferência Pix
 * 
 * @param fromWalletId ID da carteira de origem
 * @param toPixKey Chave Pix de destino
 * @param amount Valor a ser transferido (deve ser maior que zero)
 */
public record PixTransferRequest(
    @NotNull(message = "Source wallet ID is required")
    UUID fromWalletId,
    
    @NotBlank(message = "Destination Pix key is required")
    String toPixKey,
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    BigDecimal amount
) {}
