package com.pixservice.adapter.rest.dto;

import java.math.BigDecimal;

/**
 * DTO de resposta para saldo
 * 
 * @param balance Saldo da carteira
 */
public record BalanceResponse(
    BigDecimal balance
) {}
