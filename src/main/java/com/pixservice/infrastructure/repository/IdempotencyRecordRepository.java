package com.pixservice.infrastructure.repository;

import com.pixservice.domain.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório para IdempotencyRecord
 */
@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    
    /**
     * Busca registro de idempotência por scope e key
     */
    Optional<IdempotencyRecord> findByScopeAndIdempotencyKey(String scope, String idempotencyKey);
    
    /**
     * Deleta registros expirados
     */
    void deleteByExpiresAtBefore(Instant instant);
    
    /**
     * Conta registros expirados
     */
    @Query("SELECT COUNT(ir) FROM IdempotencyRecord ir WHERE ir.expiresAt < :instant")
    long countExpiredRecords(@Param("instant") Instant instant);
}
