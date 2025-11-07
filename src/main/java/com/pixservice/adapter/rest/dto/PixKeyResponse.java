package com.pixservice.adapter.rest.dto;

import com.pixservice.domain.enums.PixKeyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de resposta para chave Pix
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PixKeyResponse {
    
    private UUID id;
    private PixKeyType keyType;
    private String keyValue;
    private UUID walletId;
    private Instant createdAt;
}
