package com.pixservice.integration;

import com.pixservice.application.usecase.*;
import com.pixservice.domain.entity.PixTransfer;
import com.pixservice.domain.entity.Wallet;
import com.pixservice.domain.enums.PixKeyType;
import com.pixservice.domain.enums.PixTransferStatus;
import com.pixservice.domain.enums.WebhookEventType;
import com.pixservice.infrastructure.repository.PixTransferRepository;
import com.pixservice.infrastructure.repository.WalletRepository;
import com.pixservice.infrastructure.repository.WebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração para webhooks de Pix
 */
@SpringBootTest
@ActiveProfiles("test")
class WebhookIntegrationTest {
    
    @Autowired
    private CreateWalletUseCase createWalletUseCase;
    
    @Autowired
    private DepositUseCase depositUseCase;
    
    @Autowired
    private PixTransferUseCase pixTransferUseCase;
    
    @Autowired
    private RegisterPixKeyUseCase registerPixKeyUseCase;
    
    @Autowired
    private ProcessPixWebhookUseCase processPixWebhookUseCase;
    
    @Autowired
    private WalletRepository walletRepository;
    
    @Autowired
    private PixTransferRepository pixTransferRepository;
    
    @Autowired
    private WebhookEventRepository webhookEventRepository;
    
    @Test
    @Transactional
    void shouldProcessConfirmationWebhookSuccessfully() {
        // Arrange - Setup wallets and transfer
        Wallet sourceWallet = createWalletUseCase.execute("user-source-webhook");
        Wallet destWallet = createWalletUseCase.execute("user-dest-webhook");
        
        depositUseCase.execute(sourceWallet.getId(), BigDecimal.valueOf(500), UUID.randomUUID().toString());
        registerPixKeyUseCase.execute(destWallet.getId(), PixKeyType.EMAIL, "webhook@example.com");
        
        PixTransfer transfer = pixTransferUseCase.execute(
                sourceWallet.getId(),
                "webhook@example.com",
                BigDecimal.valueOf(100),
                UUID.randomUUID().toString()
        );
        
        String endToEndId = transfer.getEndToEndId();
        String eventId = UUID.randomUUID().toString();
        
        // Act - Process confirmation webhook
        processPixWebhookUseCase.execute(eventId, endToEndId, WebhookEventType.CONFIRMED, Instant.now());
        
        // Assert
        PixTransfer updatedTransfer = pixTransferRepository.findByEndToEndId(endToEndId).orElseThrow();
        assertThat(updatedTransfer.getStatus()).isEqualTo(PixTransferStatus.CONFIRMED);
        assertThat(updatedTransfer.getConfirmedAt()).isNotNull();
        
        // Destination wallet should have received the funds
        Wallet updatedDestWallet = walletRepository.findById(destWallet.getId()).orElseThrow();
        assertThat(updatedDestWallet.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(100));
        
        // Source wallet should have debited
        Wallet updatedSourceWallet = walletRepository.findById(sourceWallet.getId()).orElseThrow();
        assertThat(updatedSourceWallet.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(400));
    }
    
    @Test
    @Transactional
    void shouldProcessRejectionWebhookAndRefund() {
        // Arrange
        Wallet sourceWallet = createWalletUseCase.execute("user-rejection");
        Wallet destWallet = createWalletUseCase.execute("user-dest-rejection");
        
        depositUseCase.execute(sourceWallet.getId(), BigDecimal.valueOf(500), UUID.randomUUID().toString());
        registerPixKeyUseCase.execute(destWallet.getId(), PixKeyType.EMAIL, "rejection@example.com");
        
        PixTransfer transfer = pixTransferUseCase.execute(
                sourceWallet.getId(),
                "rejection@example.com",
                BigDecimal.valueOf(100),
                UUID.randomUUID().toString()
        );
        
        String endToEndId = transfer.getEndToEndId();
        String eventId = UUID.randomUUID().toString();
        
        // Act - Process rejection webhook
        processPixWebhookUseCase.execute(eventId, endToEndId, WebhookEventType.REJECTED, Instant.now());
        
        // Assert
        PixTransfer updatedTransfer = pixTransferRepository.findByEndToEndId(endToEndId).orElseThrow();
        assertThat(updatedTransfer.getStatus()).isEqualTo(PixTransferStatus.REJECTED);
        assertThat(updatedTransfer.getRejectedAt()).isNotNull();
        
        // Source wallet should have been refunded
        Wallet updatedSourceWallet = walletRepository.findById(sourceWallet.getId()).orElseThrow();
        assertThat(updatedSourceWallet.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(500));
        
        // Destination wallet should remain empty
        Wallet updatedDestWallet = walletRepository.findById(destWallet.getId()).orElseThrow();
        assertThat(updatedDestWallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }
    
    @Test
    @Transactional
    void shouldHandleDuplicateWebhookEvents() throws Exception {
        // Arrange
        Wallet sourceWallet = createWalletUseCase.execute("user-duplicate-webhook");
        Wallet destWallet = createWalletUseCase.execute("user-dest-duplicate");
        
        depositUseCase.execute(sourceWallet.getId(), BigDecimal.valueOf(500), UUID.randomUUID().toString());
        registerPixKeyUseCase.execute(destWallet.getId(), PixKeyType.EMAIL, "duplicate@example.com");
        
        PixTransfer transfer = pixTransferUseCase.execute(
                sourceWallet.getId(),
                "duplicate@example.com",
                BigDecimal.valueOf(100),
                UUID.randomUUID().toString()
        );
        
        String endToEndId = transfer.getEndToEndId();
        String eventId = UUID.randomUUID().toString();
        Instant occurredAt = Instant.now();
        
        int numberOfThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        
        // Act - Process same webhook multiple times concurrently
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                processPixWebhookUseCase.execute(eventId, endToEndId, WebhookEventType.CONFIRMED, occurredAt);
            }, executor);
            futures.add(future);
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // Assert - Should process only once
        PixTransfer updatedTransfer = pixTransferRepository.findByEndToEndId(endToEndId).orElseThrow();
        assertThat(updatedTransfer.getStatus()).isEqualTo(PixTransferStatus.CONFIRMED);
        
        // Destination should receive funds only once
        Wallet updatedDestWallet = walletRepository.findById(destWallet.getId()).orElseThrow();
        assertThat(updatedDestWallet.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(100));
        
        // Should have only one webhook event record
        long eventCount = webhookEventRepository.findByEndToEndId(endToEndId).size();
        assertThat(eventCount).isEqualTo(1);
        
        executor.shutdown();
    }
    
    @Test
    @Transactional
    void shouldHandleOutOfOrderWebhookEvents() {
        // Arrange
        Wallet sourceWallet = createWalletUseCase.execute("user-out-of-order");
        Wallet destWallet = createWalletUseCase.execute("user-dest-out-of-order");
        
        depositUseCase.execute(sourceWallet.getId(), BigDecimal.valueOf(500), UUID.randomUUID().toString());
        registerPixKeyUseCase.execute(destWallet.getId(), PixKeyType.EMAIL, "outoforder@example.com");
        
        PixTransfer transfer = pixTransferUseCase.execute(
                sourceWallet.getId(),
                "outoforder@example.com",
                BigDecimal.valueOf(100),
                UUID.randomUUID().toString()
        );
        
        String endToEndId = transfer.getEndToEndId();
        
        // Act - Process CONFIRMED event
        processPixWebhookUseCase.execute(
                "event-confirmed", 
                endToEndId, 
                WebhookEventType.CONFIRMED, 
                Instant.now()
        );
        
        PixTransfer afterConfirm = pixTransferRepository.findByEndToEndId(endToEndId).orElseThrow();
        assertThat(afterConfirm.getStatus()).isEqualTo(PixTransferStatus.CONFIRMED);
        
        // Try to process REJECTED after CONFIRMED (out of order/late arrival)
        try {
            processPixWebhookUseCase.execute(
                    "event-rejected",
                    endToEndId,
                    WebhookEventType.REJECTED,
                    Instant.now().minusSeconds(10) // Earlier timestamp
            );
        } catch (Exception e) {
            // Expected to fail due to state machine validation
            assertThat(e).isInstanceOf(IllegalStateException.class);
        }
        
        // Assert - Status should remain CONFIRMED
        PixTransfer finalTransfer = pixTransferRepository.findByEndToEndId(endToEndId).orElseThrow();
        assertThat(finalTransfer.getStatus()).isEqualTo(PixTransferStatus.CONFIRMED);
    }
}
