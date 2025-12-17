package com.pixservice.adapter.rest.dto;

import com.pixservice.domain.enums.PixKeyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO para registrar chave Pix
 * 
 * @param keyType Tipo da chave Pix (CPF, EMAIL, PHONE, etc.)
 * @param keyValue Valor da chave Pix
 */
public record RegisterPixKeyRequest(
    @NotNull(message = "Key type is required")
    PixKeyType keyType,
    
    @NotBlank(message = "Key value is required")
    String keyValue
) {}
