package com.pixservice.infrastructure.repository;

import com.pixservice.domain.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório para WebhookEvent
 */
@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
    
    /**
     * Busca evento por eventId
     */
    Optional<WebhookEvent> findByEventId(String eventId);
    
    /**
     * Busca eventos por endToEndId
     */
    List<WebhookEvent> findByEndToEndId(String endToEndId);
    
    /**
     * Busca eventos não processados
     */
    @Query("SELECT we FROM WebhookEvent we WHERE we.processed = false ORDER BY we.occurredAt ASC")
    List<WebhookEvent> findUnprocessedEvents();
    
    /**
     * Verifica se evento já foi recebido
     */
    boolean existsByEventId(String eventId);
}
