package com.pixservice.domain.entity;

import com.pixservice.domain.enums.PixTransferStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Testes unitários para PixTransfer (State Machine)
 */
class PixTransferTest {
    
    @Test
    void shouldCreateTransferInPendingStatus() {
        PixTransfer transfer = PixTransfer.builder()
                .endToEndId("E123456")
                .fromWalletId(UUID.randomUUID())
                .toPixKey("test@example.com")
                .amount(BigDecimal.valueOf(100))
                .build();
        
        assertThat(transfer.getStatus()).isEqualTo(PixTransferStatus.PENDING);
        assertThat(transfer.isPending()).isTrue();
    }
    
    @Test
    void shouldConfirmTransfer() {
        PixTransfer transfer = PixTransfer.builder()
                .endToEndId("E123456")
                .fromWalletId(UUID.randomUUID())
                .toPixKey("test@example.com")
                .amount(BigDecimal.valueOf(100))
                .status(PixTransferStatus.PENDING)
                .build();
        
        transfer.confirm();
        
        assertThat(transfer.getStatus()).isEqualTo(PixTransferStatus.CONFIRMED);
        assertThat(transfer.isConfirmed()).isTrue();
        assertThat(transfer.getConfirmedAt()).isNotNull();
    }
    
    @Test
    void shouldBeIdempotentWhenConfirmingAlreadyConfirmedTransfer() {
        PixTransfer transfer = PixTransfer.builder()
                .endToEndId("E123456")
                .fromWalletId(UUID.randomUUID())
                .toPixKey("test@example.com")
                .amount(BigDecimal.valueOf(100))
                .status(PixTransferStatus.CONFIRMED)
                .build();
        
        transfer.confirm();
        
        assertThat(transfer.getStatus()).isEqualTo(PixTransferStatus.CONFIRMED);
    }
    
    @Test
    void shouldThrowExceptionWhenConfirmingRejectedTransfer() {
        PixTransfer transfer = PixTransfer.builder()
                .endToEndId("E123456")
                .fromWalletId(UUID.randomUUID())
                .toPixKey("test@example.com")
                .amount(BigDecimal.valueOf(100))
                .status(PixTransferStatus.REJECTED)
                .build();
        
        assertThatThrownBy(transfer::confirm)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot confirm a rejected transfer");
    }
    
    @Test
    void shouldRejectTransfer() {
        PixTransfer transfer = PixTransfer.builder()
                .endToEndId("E123456")
                .fromWalletId(UUID.randomUUID())
                .toPixKey("test@example.com")
                .amount(BigDecimal.valueOf(100))
                .status(PixTransferStatus.PENDING)
                .build();
        
        transfer.reject("Insufficient funds");
        
        assertThat(transfer.getStatus()).isEqualTo(PixTransferStatus.REJECTED);
        assertThat(transfer.isRejected()).isTrue();
        assertThat(transfer.getRejectionReason()).isEqualTo("Insufficient funds");
        assertThat(transfer.getRejectedAt()).isNotNull();
    }
    
    @Test
    void shouldThrowExceptionWhenRejectingConfirmedTransfer() {
        PixTransfer transfer = PixTransfer.builder()
                .endToEndId("E123456")
                .fromWalletId(UUID.randomUUID())
                .toPixKey("test@example.com")
                .amount(BigDecimal.valueOf(100))
                .status(PixTransferStatus.CONFIRMED)
                .build();
        
        assertThatThrownBy(() -> transfer.reject("Test reason"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot reject a confirmed transfer");
    }
}
