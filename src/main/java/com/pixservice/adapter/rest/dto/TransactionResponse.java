package com.pixservice.adapter.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pixservice.domain.enums.TransactionType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO de resposta para transação
 * 
 * @param id Identificador único da transação
 * @param walletId ID da carteira associada
 * @param type Tipo da transação (DEPOSIT, WITHDRAW, PIX_TRANSFER_OUT, PIX_TRANSFER_IN)
 * @param amount Valor da transação
 * @param balanceAfter Saldo após a transação
 * @param description Descrição da transação
 * @param endToEndId End-to-end ID (para transferências Pix)
 * @param createdAt Data/hora da transação
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionResponse(
    UUID id,
    UUID walletId,
    TransactionType type,
    BigDecimal amount,
    BigDecimal balanceAfter,
    String description,
    String endToEndId,
    Instant createdAt
) {}
