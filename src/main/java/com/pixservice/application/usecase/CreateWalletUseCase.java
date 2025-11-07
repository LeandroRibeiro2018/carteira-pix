package com.pixservice.application.usecase;

import com.pixservice.domain.entity.Wallet;
import com.pixservice.infrastructure.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use Case: Criar Carteira
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreateWalletUseCase {
    
    private final WalletRepository walletRepository;
    
    @Transactional
    public Wallet execute(String userId) {
        log.info("Criando carteira para usuário: {}", userId);
        
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("ID do usuário é obrigatório");
        }
        
        if (walletRepository.existsByUserId(userId)) {
            throw new IllegalArgumentException("Carteira já existe para o usuário: " + userId);
        }
        
        Wallet wallet = Wallet.builder()
                .userId(userId)
                .build();
        
        Wallet savedWallet = walletRepository.save(wallet);
        log.info("Carteira criada com sucesso: walletId={}", savedWallet.getId());
        
        return savedWallet;
    }
}
