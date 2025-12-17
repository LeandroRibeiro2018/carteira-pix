package com.pixservice.adapter.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO para criar carteira
 * 
 * @param userId Identificador único do usuário
 */
public record CreateWalletRequest(
    @NotBlank(message = "UserId is required")
    String userId
) {}
