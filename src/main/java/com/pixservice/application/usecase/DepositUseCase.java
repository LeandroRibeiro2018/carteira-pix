package com.pixservice.application.usecase;

import com.pixservice.application.exception.ApplicationException;
import com.pixservice.domain.entity.Transaction;
import com.pixservice.domain.entity.Wallet;
import com.pixservice.infrastructure.repository.TransactionRepository;
import com.pixservice.infrastructure.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Use Case: Depósito
 * Adiciona crédito à carteira com garantia de idempotência
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DepositUseCase {
    
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    
    @Transactional
    public Transaction execute(UUID walletId, BigDecimal amount, String idempotencyKey) {
        log.info("Processing deposit: walletId={}, amount={}, idempotencyKey={}", 
                walletId, amount, idempotencyKey);
        
        // Validações
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        
        // Verifica idempotência
        if (idempotencyKey != null && transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            Transaction existingTransaction = transactionRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow();
            log.info("Duplicate deposit request detected, returning existing transaction: {}", 
                    existingTransaction.getId());
            return existingTransaction;
        }
        
        // Busca carteira com lock pessimista
        Wallet wallet = walletRepository.findByIdWithLock(walletId)
                .orElseThrow(() -> new ApplicationException("Wallet not found: " + walletId));
        
        // Realiza depósito
        wallet.deposit(amount);
        walletRepository.save(wallet);
        
        // Registra no ledger
        Transaction transaction = Transaction.deposit(walletId, amount, wallet.getBalance(), idempotencyKey);
        Transaction savedTransaction = transactionRepository.save(transaction);
        
        log.info("Deposit processed successfully: transactionId={}, newBalance={}", 
                savedTransaction.getId(), wallet.getBalance());
        
        return savedTransaction;
    }
}
