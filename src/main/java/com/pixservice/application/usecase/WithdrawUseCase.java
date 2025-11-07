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
 * Use Case: Saque
 * Remove débito da carteira com validação de saldo
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawUseCase {
    
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    
    @Transactional
    public Transaction execute(UUID walletId, BigDecimal amount, String idempotencyKey) {
        log.info("Processing withdrawal: walletId={}, amount={}, idempotencyKey={}", 
                walletId, amount, idempotencyKey);
        
        // Validações
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        
        // Verifica idempotência
        if (idempotencyKey != null && transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            Transaction existingTransaction = transactionRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow();
            log.info("Duplicate withdrawal request detected, returning existing transaction: {}", 
                    existingTransaction.getId());
            return existingTransaction;
        }
        
        // Busca carteira com lock pessimista
        Wallet wallet = walletRepository.findByIdWithLock(walletId)
                .orElseThrow(() -> new ApplicationException("Wallet not found: " + walletId));
        
        // Valida saldo
        if (!wallet.hasSufficientBalance(amount)) {
            throw new ApplicationException("Insufficient balance. Current: " + wallet.getBalance() + 
                    ", Required: " + amount);
        }
        
        // Realiza saque
        wallet.withdraw(amount);
        walletRepository.save(wallet);
        
        // Registra no ledger
        Transaction transaction = Transaction.withdrawal(walletId, amount, wallet.getBalance(), idempotencyKey);
        Transaction savedTransaction = transactionRepository.save(transaction);
        
        log.info("Withdrawal processed successfully: transactionId={}, newBalance={}", 
                savedTransaction.getId(), wallet.getBalance());
        
        return savedTransaction;
    }
}
