package com.pixservice.domain.enums;

/**
 * Status de uma transferência Pix
 * State Machine: PENDING -> CONFIRMED ou REJECTED
 */
public enum PixTransferStatus {
    PENDING,
    CONFIRMED,
    REJECTED
}
