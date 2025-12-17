package com.pixservice.adapter.rest.dto;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * DTO de resposta para saldo
 * 
 * @param balance Saldo da carteira
 */
@Builder
public record BalanceResponse(
    BigDecimal balance
) {}
