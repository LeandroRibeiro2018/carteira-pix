package com.pixservice.infrastructure.repository;

import com.pixservice.domain.entity.PixTransfer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositório para PixTransfer com suporte a locking
 */
@Repository
public interface PixTransferRepository extends JpaRepository<PixTransfer, UUID> {
    
    /**
     * Busca transferência por endToEndId
     */
    Optional<PixTransfer> findByEndToEndId(String endToEndId);
    
    /**
     * Busca transferência por endToEndId com lock pessimista
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pt FROM PixTransfer pt WHERE pt.endToEndId = :endToEndId")
    Optional<PixTransfer> findByEndToEndIdWithLock(@Param("endToEndId") String endToEndId);
    
    /**
     * Busca transferência por idempotency key
     */
    Optional<PixTransfer> findByIdempotencyKey(String idempotencyKey);
    
    /**
     * Verifica se existe transferência com idempotency key
     */
    boolean existsByIdempotencyKey(String idempotencyKey);
    
    /**
     * Verifica se existe transferência com endToEndId
     */
    boolean existsByEndToEndId(String endToEndId);
}
