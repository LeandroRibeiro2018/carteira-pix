package com.pixservice.infrastructure.repository;

import com.pixservice.domain.entity.PixKey;
import com.pixservice.domain.enums.PixKeyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório para PixKey
 */
@Repository
public interface PixKeyRepository extends JpaRepository<PixKey, UUID> {
    
    /**
     * Busca chave Pix por tipo e valor
     */
    Optional<PixKey> findByKeyTypeAndKeyValue(PixKeyType keyType, String keyValue);
    
    /**
     * Busca chave Pix por valor (independente do tipo)
     */
    Optional<PixKey> findByKeyValue(String keyValue);
    
    /**
     * Lista todas as chaves de uma carteira
     */
    @Query("SELECT pk FROM PixKey pk WHERE pk.wallet.id = :walletId")
    List<PixKey> findByWalletId(@Param("walletId") UUID walletId);
    
    /**
     * Verifica se chave Pix já existe
     */
    boolean existsByKeyValue(String keyValue);
}
