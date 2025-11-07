package com.pixservice.application.usecase;

import com.pixservice.application.exception.ApplicationException;
import com.pixservice.domain.entity.Transaction;
import com.pixservice.domain.entity.Wallet;
import com.pixservice.infrastructure.repository.TransactionRepository;
import com.pixservice.infrastructure.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para DepositUseCase
 */
@ExtendWith(MockitoExtension.class)
class DepositUseCaseTest {
    
    @Mock
    private WalletRepository walletRepository;
    
    @Mock
    private TransactionRepository transactionRepository;
    
    @InjectMocks
    private DepositUseCase depositUseCase;
    
    @Test
    void shouldDepositSuccessfully() {
        UUID walletId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        BigDecimal amount = BigDecimal.valueOf(100);
        
        Wallet wallet = Wallet.builder()
                .id(walletId)
                .userId("user123")
                .balance(BigDecimal.ZERO)
                .build();
        
        when(transactionRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false);
        when(walletRepository.findByIdWithLock(walletId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
        
        Transaction result = depositUseCase.execute(walletId, amount, idempotencyKey);
        
        assertThat(result).isNotNull();
        assertThat(wallet.getBalance()).isEqualByComparingTo(amount);
        
        verify(walletRepository).findByIdWithLock(walletId);
        verify(walletRepository).save(wallet);
        verify(transactionRepository).save(any(Transaction.class));
    }
    
    @Test
    void shouldReturnExistingTransactionWhenDuplicateRequest() {
        UUID walletId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        BigDecimal amount = BigDecimal.valueOf(100);
        
        Transaction existingTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .walletId(walletId)
                .amount(amount)
                .idempotencyKey(idempotencyKey)
                .build();
        
        when(transactionRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(true);
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(existingTransaction));
        
        Transaction result = depositUseCase.execute(walletId, amount, idempotencyKey);
        
        assertThat(result).isEqualTo(existingTransaction);
        verify(walletRepository, never()).findByIdWithLock(any());
        verify(walletRepository, never()).save(any());
    }
    
    @Test
    void shouldThrowExceptionWhenWalletNotFound() {
        UUID walletId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        BigDecimal amount = BigDecimal.valueOf(100);
        
        when(transactionRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false);
        when(walletRepository.findByIdWithLock(walletId)).thenReturn(Optional.empty());
        
        assertThatThrownBy(() -> depositUseCase.execute(walletId, amount, idempotencyKey))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("Wallet not found");
    }
    
    @Test
    void shouldThrowExceptionWhenAmountIsInvalid() {
        UUID walletId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        
        assertThatThrownBy(() -> depositUseCase.execute(walletId, BigDecimal.ZERO, idempotencyKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount must be greater than zero");
        
        assertThatThrownBy(() -> depositUseCase.execute(walletId, BigDecimal.valueOf(-100), idempotencyKey))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
