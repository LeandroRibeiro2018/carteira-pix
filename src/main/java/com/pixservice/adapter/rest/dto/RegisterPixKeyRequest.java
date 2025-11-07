package com.pixservice.adapter.rest.dto;

import com.pixservice.domain.enums.PixKeyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para registrar chave Pix
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterPixKeyRequest {
    
    @NotNull(message = "Key type is required")
    private PixKeyType keyType;
    
    @NotBlank(message = "Key value is required")
    private String keyValue;
}
