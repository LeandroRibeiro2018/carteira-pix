package com.pixservice.application.usecase;

import com.pixservice.application.exception.ApplicationException;
import com.pixservice.domain.entity.PixKey;
import com.pixservice.domain.entity.Wallet;
import com.pixservice.domain.enums.PixKeyType;
import com.pixservice.infrastructure.repository.PixKeyRepository;
import com.pixservice.infrastructure.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Use Case: Registrar Chave Pix
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegisterPixKeyUseCase {
    
    private final WalletRepository walletRepository;
    private final PixKeyRepository pixKeyRepository;
    
    @Transactional
    public PixKey execute(UUID walletId, PixKeyType keyType, String keyValue) {
        log.info("Registering Pix key for walletId: {}, type: {}, value: {}", walletId, keyType, keyValue);
        
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ApplicationException("Wallet not found: " + walletId));
        
        if (pixKeyRepository.existsByKeyValue(keyValue)) {
            throw new ApplicationException("Pix key already registered: " + keyValue);
        }
        
        PixKey pixKey = PixKey.builder()
                .keyType(keyType)
                .keyValue(keyValue)
                .wallet(wallet)
                .build();
        
        pixKey.validate();
        
        PixKey savedPixKey = pixKeyRepository.save(pixKey);
        log.info("Pix key registered successfully: pixKeyId={}", savedPixKey.getId());
        
        return savedPixKey;
    }
}
