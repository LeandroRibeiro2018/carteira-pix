package com.pixservice.adapter.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO de resposta para carteira
 * 
 * @param id Identificador único da carteira
 * @param userId Identificador do usuário proprietário
 * @param balance Saldo atual da carteira
 * @param createdAt Data/hora de criação da carteira
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WalletResponse(
    UUID id,
    String userId,
    BigDecimal balance,
    Instant createdAt
) {}
