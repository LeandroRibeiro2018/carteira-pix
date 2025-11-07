package com.pixservice.adapter.rest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO para transferência Pix
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PixTransferRequest {
    
    @NotNull(message = "Source wallet ID is required")
    private UUID fromWalletId;
    
    @NotBlank(message = "Destination Pix key is required")
    private String toPixKey;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;
}
