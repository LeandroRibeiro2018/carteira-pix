package com.pixservice.domain.entity;

import com.pixservice.domain.enums.WebhookEventType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidade WebhookEvent - Armazena eventos recebidos via webhook
 * Garante idempotência por eventId único
 */
@Entity
@Table(name = "webhook_events", indexes = {
    @Index(name = "idx_event_id", columnList = "event_id", unique = true),
    @Index(name = "idx_end_to_end_id", columnList = "end_to_end_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;
    
    @Column(name = "end_to_end_id", nullable = false)
    private String endToEndId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private WebhookEventType eventType;
    
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    
    @Column(nullable = false)
    @Builder.Default
    private boolean processed = false;
    
    @Column(name = "processed_at")
    private Instant processedAt;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant receivedAt;
    
    @Column(name = "error_message", length = 1000)
    private String errorMessage;
    
    public void markAsProcessed() {
        this.processed = true;
        this.processedAt = Instant.now();
    }
    
    public void markAsError(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
