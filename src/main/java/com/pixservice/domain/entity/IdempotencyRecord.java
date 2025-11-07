package com.pixservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidade IdempotencyRecord - Garante idempotência de operações
 * Armazena o resultado de operações para retornar a mesma resposta em requisições duplicadas
 */
@Entity
@Table(name = "idempotency_records", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"scope", "idempotency_key"})
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false, length = 50)
    private String scope;
    
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;
    
    @Column(name = "resource_id")
    private String resourceId;
    
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;
    
    @Column(name = "response_status")
    private Integer responseStatus;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
