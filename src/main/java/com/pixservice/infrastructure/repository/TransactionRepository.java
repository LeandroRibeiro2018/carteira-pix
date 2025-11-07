package com.pixservice.infrastructure.repository;

import com.pixservice.domain.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório para Transaction (Ledger)
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    
    /**
     * Lista todas as transações de uma carteira ordenadas por data
     */
    @Query("SELECT t FROM Transaction t WHERE t.walletId = :walletId ORDER BY t.createdAt ASC")
    List<Transaction> findByWalletIdOrderByCreatedAtAsc(@Param("walletId") UUID walletId);
    
    /**
     * Lista transações até um timestamp específico (para saldo histórico)
     */
    @Query("SELECT t FROM Transaction t WHERE t.walletId = :walletId AND t.createdAt <= :timestamp ORDER BY t.createdAt ASC")
    List<Transaction> findByWalletIdAndCreatedAtBefore(@Param("walletId") UUID walletId, 
                                                        @Param("timestamp") Instant timestamp);
    
    /**
     * Busca transação por endToEndId
     */
    Optional<Transaction> findByEndToEndId(String endToEndId);
    
    /**
     * Busca transação por idempotency key
     */
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
    
    /**
     * Verifica se existe transação com idempotency key
     */
    boolean existsByIdempotencyKey(String idempotencyKey);
}
