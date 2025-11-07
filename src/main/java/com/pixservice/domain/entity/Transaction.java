package com.pixservice.domain.entity;

import com.pixservice.domain.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidade Transaction - Ledger imutável de todas as operações
 * Implementa o conceito de Event Sourcing para auditoria completa
 */
@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_wallet_created_at", columnList = "wallet_id,created_at"),
    @Index(name = "idx_end_to_end_id", columnList = "end_to_end_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Column(precision = 19, scale = 2)
    private BigDecimal balanceAfter;
    
    @Column(length = 500)
    private String description;
    
    @Column(name = "end_to_end_id")
    private String endToEndId;
    
    @Column(name = "idempotency_key")
    private String idempotencyKey;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(length = 1000)
    private String metadata;
    
    // Factory methods
    
    public static Transaction deposit(UUID walletId, BigDecimal amount, BigDecimal balanceAfter, String idempotencyKey) {
        return Transaction.builder()
                .walletId(walletId)
                .type(TransactionType.DEPOSIT)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .description("Deposit")
                .idempotencyKey(idempotencyKey)
                .build();
    }
    
    public static Transaction withdrawal(UUID walletId, BigDecimal amount, BigDecimal balanceAfter, String idempotencyKey) {
        return Transaction.builder()
                .walletId(walletId)
                .type(TransactionType.WITHDRAWAL)
                .amount(amount.negate())
                .balanceAfter(balanceAfter)
                .description("Withdrawal")
                .idempotencyKey(idempotencyKey)
                .build();
    }
    
    public static Transaction pixTransferOut(UUID walletId, BigDecimal amount, BigDecimal balanceAfter, 
                                            String endToEndId, String idempotencyKey, String toPixKey) {
        return Transaction.builder()
                .walletId(walletId)
                .type(TransactionType.PIX_OUT)
                .amount(amount.negate())
                .balanceAfter(balanceAfter)
                .description("Pix transfer to " + toPixKey)
                .endToEndId(endToEndId)
                .idempotencyKey(idempotencyKey)
                .build();
    }
    
    public static Transaction pixTransferIn(UUID walletId, BigDecimal amount, BigDecimal balanceAfter, 
                                           String endToEndId, String fromPixKey) {
        return Transaction.builder()
                .walletId(walletId)
                .type(TransactionType.PIX_IN)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .description("Pix transfer from " + fromPixKey)
                .endToEndId(endToEndId)
                .build();
    }
}
