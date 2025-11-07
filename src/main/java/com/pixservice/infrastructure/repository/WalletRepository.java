package com.pixservice.infrastructure.repository;

import com.pixservice.domain.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositório para Wallet com suporte a pessimistic locking
 */
@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    
    /**
     * Busca carteira com lock pessimista para evitar race conditions
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdWithLock(@Param("id") UUID id);
    
    /**
     * Busca carteira por userId
     */
    Optional<Wallet> findByUserId(String userId);
    
    /**
     * Verifica se userId já existe
     */
    boolean existsByUserId(String userId);
}
