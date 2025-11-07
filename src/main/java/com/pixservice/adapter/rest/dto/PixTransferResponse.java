package com.pixservice.adapter.rest.dto;

import com.pixservice.domain.enums.PixTransferStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de resposta para transferência Pix
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PixTransferResponse {
    
    private String endToEndId;
    private PixTransferStatus status;
    private BigDecimal amount;
    private String toPixKey;
    private Instant createdAt;
}
