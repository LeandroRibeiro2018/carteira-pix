package com.pixservice.integration;

import com.pixservice.application.usecase.CreateWalletUseCase;
import com.pixservice.application.usecase.DepositUseCase;
import com.pixservice.application.usecase.PixTransferUseCase;
import com.pixservice.application.usecase.RegisterPixKeyUseCase;
import com.pixservice.domain.entity.PixTransfer;
import com.pixservice.domain.entity.Transaction;
import com.pixservice.domain.entity.Wallet;
import com.pixservice.domain.enums.PixKeyType;
import com.pixservice.infrastructure.repository.PixTransferRepository;
import com.pixservice.infrastructure.repository.TransactionRepository;
import com.pixservice.infrastructure.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração para cenários de concorrência
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrencyIntegrationTest {
    
    @Autowired
    private CreateWalletUseCase createWalletUseCase;
    
    @Autowired
    private DepositUseCase depositUseCase;
    
    @Autowired
    private PixTransferUseCase pixTransferUseCase;
    
    @Autowired
    private RegisterPixKeyUseCase registerPixKeyUseCase;
    
    @Autowired
    private WalletRepository walletRepository;
    
    @Autowired
    private PixTransferRepository pixTransferRepository;
    
    @Autowired
    private TransactionRepository transactionRepository;
    
    @AfterEach
    void cleanup() {
        // Clean up in reverse order of dependencies
        pixTransferRepository.deleteAll();
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
    }
    
    @Test
    void shouldHandleConcurrentDeposits() throws Exception {
        // Arrange - Create wallet with unique user ID
        Wallet wallet = createWalletUseCase.execute("user-concurrent-deposits-" + UUID.randomUUID());
        UUID walletId = wallet.getId();
        
        int numberOfThreads = 10;
        BigDecimal depositAmount = BigDecimal.valueOf(10);
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        
        // Act - Concurrent deposits with different idempotency keys
        List<CompletableFuture<Transaction>> futures = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            final int index = i;
            CompletableFuture<Transaction> future = CompletableFuture.supplyAsync(() -> {
                return depositUseCase.execute(walletId, depositAmount, "deposit-key-" + index);
            }, executor);
            futures.add(future);
        }
        
        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // Assert
        Wallet updatedWallet = walletRepository.findById(walletId).orElseThrow();
        BigDecimal expectedBalance = depositAmount.multiply(BigDecimal.valueOf(numberOfThreads));
        
        assertThat(updatedWallet.getBalance()).isEqualByComparingTo(expectedBalance);
        
        executor.shutdown();
    }
    
    @Test
    void shouldHandleDuplicateRequestsWithSameIdempotencyKey() throws Exception {
        // Arrange
        Wallet wallet = createWalletUseCase.execute("user-idempotency-" + UUID.randomUUID());
        UUID walletId = wallet.getId();
        
        String idempotencyKey = UUID.randomUUID().toString();
        BigDecimal depositAmount = BigDecimal.valueOf(100);
        
        int numberOfThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        
        // Act - Multiple requests with same idempotency key
        List<CompletableFuture<Transaction>> futures = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            CompletableFuture<Transaction> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return depositUseCase.execute(walletId, depositAmount, idempotencyKey);
                } catch (Exception e) {
                    // Expected: some threads will fail due to constraint violation
                    // Wait a bit and retry to get the existing transaction
                    try {
                        Thread.sleep(100);
                        return transactionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
                    } catch (Exception ex) {
                        return null;
                    }
                }
            }, executor);
            futures.add(future);
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        List<Transaction> transactions = futures.stream()
                .map(CompletableFuture::join)
                .filter(t -> t != null)
                .collect(Collectors.toList());
        
        // Assert - All should return same transaction (or null if failed)
        assertThat(transactions).isNotEmpty();
        Transaction firstTransaction = transactions.get(0);
        transactions.forEach(t -> assertThat(t.getId()).isEqualTo(firstTransaction.getId()));
        
        // Balance should reflect only ONE deposit
        Wallet updatedWallet = walletRepository.findById(walletId).orElseThrow();
        assertThat(updatedWallet.getBalance()).isEqualByComparingTo(depositAmount);
        
        executor.shutdown();
    }
    
    @Test
    void shouldHandleConcurrentPixTransfersWithSameIdempotencyKey() throws Exception {
        // Arrange - Create two wallets with unique IDs
        Wallet sourceWallet = createWalletUseCase.execute("user-source-" + UUID.randomUUID());
        Wallet destWallet = createWalletUseCase.execute("user-dest-" + UUID.randomUUID());
        
        // Add balance to source
        depositUseCase.execute(sourceWallet.getId(), BigDecimal.valueOf(1000), UUID.randomUUID().toString());
        
        // Register destination Pix key with unique email
        String pixKey = "dest-" + UUID.randomUUID() + "@example.com";
        registerPixKeyUseCase.execute(destWallet.getId(), PixKeyType.EMAIL, pixKey);
        
        String idempotencyKey = UUID.randomUUID().toString();
        BigDecimal transferAmount = BigDecimal.valueOf(100);
        
        int numberOfThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        
        // Act - Concurrent transfers with same idempotency key (simulating duplicate requests)
        List<CompletableFuture<PixTransfer>> futures = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            CompletableFuture<PixTransfer> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return pixTransferUseCase.execute(
                            sourceWallet.getId(),
                            pixKey,
                            transferAmount,
                            idempotencyKey
                    );
                } catch (Exception e) {
                    // Expected: some threads will fail due to constraint violation
                    // Wait a bit and retry to get the existing transfer
                    try {
                        Thread.sleep(100);
                        return pixTransferRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
                    } catch (Exception ex) {
                        return null;
                    }
                }
            }, executor);
            futures.add(future);
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        List<PixTransfer> transfers = futures.stream()
                .map(CompletableFuture::join)
                .filter(t -> t != null)
                .collect(Collectors.toList());
        
        // Assert - All should return same transfer (idempotent)
        assertThat(transfers).isNotEmpty();
        PixTransfer firstTransfer = transfers.get(0);
        transfers.forEach(t -> {
            assertThat(t.getEndToEndId()).isEqualTo(firstTransfer.getEndToEndId());
            assertThat(t.getId()).isEqualTo(firstTransfer.getId());
        });
        
        // Should have only ONE transfer in database
        List<PixTransfer> allTransfers = pixTransferRepository.findAll();
        long transfersWithKey = allTransfers.stream()
                .filter(t -> t.getIdempotencyKey().equals(idempotencyKey))
                .count();
        
        assertThat(transfersWithKey).isEqualTo(1);
        
        // Source wallet should have exactly one debit
        Wallet updatedSourceWallet = walletRepository.findById(sourceWallet.getId()).orElseThrow();
        BigDecimal expectedBalance = BigDecimal.valueOf(1000).subtract(transferAmount);
        assertThat(updatedSourceWallet.getBalance()).isEqualByComparingTo(expectedBalance);
        
        executor.shutdown();
    }
}
