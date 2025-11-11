package com.pixservice.domain.entity;

import com.pixservice.domain.enums.PixTransferStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidade PixTransfer - Representa uma transferência Pix
 * Implementa State Machine: PENDING -> CONFIRMED ou REJECTED
 */
@Entity
@Table(name = "pix_transfers", indexes = {
    @Index(name = "idx_end_to_end_id", columnList = "end_to_end_id", unique = true),
    @Index(name = "idx_idempotency_key", columnList = "idempotency_key", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PixTransfer {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "end_to_end_id", nullable = false, unique = true)
    private String endToEndId;
    
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;
    
    @Column(name = "from_wallet_id", nullable = false)
    private UUID fromWalletId;
    
    @Column(name = "to_pix_key", nullable = false)
    private String toPixKey;
    
    @Column(name = "to_wallet_id")
    private UUID toWalletId;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PixTransferStatus status = PixTransferStatus.PENDING;
    
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
    
    @Column(name = "confirmed_at")
    private Instant confirmedAt;
    
    @Column(name = "rejected_at")
    private Instant rejectedAt;
    
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;
    
    // State machine methods
    
    public void confirm() {
        if (status == PixTransferStatus.CONFIRMED) {
            return; // Idempotent
        }
        
        if (status == PixTransferStatus.REJECTED) {
            throw new IllegalStateException("Cannot confirm a rejected transfer");
        }
        
        this.status = PixTransferStatus.CONFIRMED;
        this.confirmedAt = Instant.now();
    }
    
    public void reject(String reason) {
        if (status == PixTransferStatus.REJECTED) {
            return; // Idempotent
        }
        
        if (status == PixTransferStatus.CONFIRMED) {
            throw new IllegalStateException("Cannot reject a confirmed transfer");
        }
        
        this.status = PixTransferStatus.REJECTED;
        this.rejectedAt = Instant.now();
        this.rejectionReason = reason;
    }
    
    public boolean isPending() {
        return status == PixTransferStatus.PENDING;
    }
    
    public boolean isConfirmed() {
        return status == PixTransferStatus.CONFIRMED;
    }
    
    public boolean isRejected() {
        return status == PixTransferStatus.REJECTED;
    }
}
