package com.pixservice.application.usecase;

import com.pixservice.application.exception.ApplicationException;
import com.pixservice.domain.entity.Transaction;
import com.pixservice.domain.entity.Wallet;
import com.pixservice.infrastructure.repository.TransactionRepository;
import com.pixservice.infrastructure.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Use Case: Consultar Saldo
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GetBalanceUseCase {
    
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    
    /**
     * Retorna saldo atual da carteira
     */
    public BigDecimal getCurrentBalance(UUID walletId) {
        log.info("Getting current balance for walletId: {}", walletId);
        
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ApplicationException("Wallet not found: " + walletId));
        
        return wallet.getBalance();
    }
    
    /**
     * Retorna saldo histórico em um timestamp específico
     * Calcula o saldo somando todas as transações até o timestamp
     */
    public BigDecimal getHistoricalBalance(UUID walletId, Instant timestamp) {
        log.info("Getting historical balance for walletId: {} at timestamp: {}", walletId, timestamp);
        
        if (!walletRepository.existsById(walletId)) {
            throw new ApplicationException("Wallet not found: " + walletId);
        }
        
        List<Transaction> transactions = transactionRepository
                .findByWalletIdAndCreatedAtBefore(walletId, timestamp);
        
        BigDecimal balance = transactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        log.info("Historical balance calculated: {} for walletId: {} at {}", balance, walletId, timestamp);
        
        return balance;
    }
}
