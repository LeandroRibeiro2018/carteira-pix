package com.pixservice.adapter.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pixservice.domain.enums.PixKeyType;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de resposta para chave Pix
 * 
 * @param id Identificador único da chave Pix
 * @param keyType Tipo da chave (CPF, EMAIL, PHONE, etc.)
 * @param keyValue Valor da chave
 * @param walletId ID da carteira associada
 * @param createdAt Data/hora de criação da chave
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PixKeyResponse(
    UUID id,
    PixKeyType keyType,
    String keyValue,
    UUID walletId,
    Instant createdAt
) {}
