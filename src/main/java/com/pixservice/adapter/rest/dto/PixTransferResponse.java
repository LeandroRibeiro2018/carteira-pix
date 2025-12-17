package com.pixservice.adapter.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pixservice.domain.enums.PixTransferStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de resposta para transferência Pix
 * 
 * @param endToEndId Identificador end-to-end da transferência
 * @param status Status da transferência (PENDING, CONFIRMED, REJECTED)
 * @param amount Valor transferido
 * @param toPixKey Chave Pix de destino
 * @param createdAt Data/hora de criação da transferência
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PixTransferResponse(
    String endToEndId,
    PixTransferStatus status,
    BigDecimal amount,
    String toPixKey,
    Instant createdAt
) {}
