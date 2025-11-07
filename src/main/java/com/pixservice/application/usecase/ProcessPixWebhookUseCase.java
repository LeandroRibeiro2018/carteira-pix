package com.pixservice.application.usecase;

import com.pixservice.application.exception.ApplicationException;
import com.pixservice.domain.entity.PixTransfer;
import com.pixservice.domain.entity.Transaction;
import com.pixservice.domain.entity.Wallet;
import com.pixservice.domain.entity.WebhookEvent;
import com.pixservice.domain.enums.PixTransferStatus;
import com.pixservice.domain.enums.WebhookEventType;
import com.pixservice.infrastructure.repository.PixTransferRepository;
import com.pixservice.infrastructure.repository.TransactionRepository;
import com.pixservice.infrastructure.repository.WalletRepository;
import com.pixservice.infrastructure.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Use Case: Processar Webhook de Pix
 * Processa eventos de confirmação/rejeição com garantia de exactly-once
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessPixWebhookUseCase {
    
    private final WebhookEventRepository webhookEventRepository;
    private final PixTransferRepository pixTransferRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    
    @Transactional
    public void execute(String eventId, String endToEndId, WebhookEventType eventType, Instant occurredAt) {
        log.info("Processando webhook: eventId={}, endToEndId={}, eventType={}, occurredAt={}", 
                eventId, endToEndId, eventType, occurredAt);
        
        // Validações
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("ID do evento é obrigatório");
        }
        if (endToEndId == null || endToEndId.isBlank()) {
            throw new IllegalArgumentException("ID end-to-end é obrigatório");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("Tipo do evento é obrigatório");
        }
        
        // Verifica idempotência por eventId - se já processado, retorna
        if (webhookEventRepository.existsByEventId(eventId)) {
            WebhookEvent existingEvent = webhookEventRepository.findByEventId(eventId).orElseThrow();
            log.info("Evento de webhook duplicado detectado: eventId={}, jáProcessado={}", 
                    eventId, existingEvent.isProcessed());
            return; // Idempotente
        }
        
        // Cria registro do evento
        WebhookEvent webhookEvent = WebhookEvent.builder()
                .eventId(eventId)
                .endToEndId(endToEndId)
                .eventType(eventType)
                .occurredAt(occurredAt)
                .processed(false)
                .build();
        
        webhookEvent = webhookEventRepository.save(webhookEvent);
        
        try {
            // Busca a transferência Pix com lock pessimista
            PixTransfer pixTransfer = pixTransferRepository.findByEndToEndIdWithLock(endToEndId)
                    .orElseThrow(() -> new ApplicationException("Pix transfer not found: " + endToEndId));
            
            // Processa evento baseado no tipo
            if (eventType == WebhookEventType.CONFIRMED) {
                processConfirmation(pixTransfer);
            } else if (eventType == WebhookEventType.REJECTED) {
                processRejection(pixTransfer);
            }
            
            // Marca evento como processado
            webhookEvent.markAsProcessed();
            webhookEventRepository.save(webhookEvent);
            
            log.info("Webhook processed successfully: eventId={}, endToEndId={}, finalStatus={}", 
                    eventId, endToEndId, pixTransfer.getStatus());
            
        } catch (Exception e) {
            log.error("Error processing webhook: eventId={}, endToEndId={}", eventId, endToEndId, e);
            webhookEvent.markAsError(e.getMessage());
            webhookEventRepository.save(webhookEvent);
            throw e;
        }
    }
    
    private void processConfirmation(PixTransfer pixTransfer) {
        log.info("Processing confirmation for transfer: endToEndId={}, currentStatus={}", 
                pixTransfer.getEndToEndId(), pixTransfer.getStatus());
        
        // Verifica estado atual
        if (pixTransfer.getStatus() == PixTransferStatus.CONFIRMED) {
            log.info("Transfer already confirmed, skipping");
            return; // Idempotente
        }
        
        if (pixTransfer.getStatus() == PixTransferStatus.REJECTED) {
            log.warn("Attempting to confirm rejected transfer: endToEndId={}", pixTransfer.getEndToEndId());
            throw new IllegalStateException("Cannot confirm a rejected transfer");
        }
        
        // Credita na carteira destino com lock pessimista
        Wallet toWallet = walletRepository.findByIdWithLock(pixTransfer.getToWalletId())
                .orElseThrow(() -> new ApplicationException("Destination wallet not found: " + 
                        pixTransfer.getToWalletId()));
        
        toWallet.deposit(pixTransfer.getAmount());
        walletRepository.save(toWallet);
        
        // Registra transação de entrada
        Transaction inTransaction = Transaction.pixTransferIn(
                pixTransfer.getToWalletId(), 
                pixTransfer.getAmount(), 
                toWallet.getBalance(), 
                pixTransfer.getEndToEndId(), 
                pixTransfer.getFromWalletId().toString());
        transactionRepository.save(inTransaction);
        
        // Atualiza status da transferência
        pixTransfer.confirm();
        pixTransferRepository.save(pixTransfer);
        
        log.info("Transfer confirmed successfully: endToEndId={}", pixTransfer.getEndToEndId());
    }
    
    private void processRejection(PixTransfer pixTransfer) {
        log.info("Processing rejection for transfer: endToEndId={}, currentStatus={}", 
                pixTransfer.getEndToEndId(), pixTransfer.getStatus());
        
        // Verifica estado atual
        if (pixTransfer.getStatus() == PixTransferStatus.REJECTED) {
            log.info("Transfer already rejected, skipping");
            return; // Idempotente
        }
        
        if (pixTransfer.getStatus() == PixTransferStatus.CONFIRMED) {
            log.warn("Attempting to reject confirmed transfer: endToEndId={}", pixTransfer.getEndToEndId());
            throw new IllegalStateException("Cannot reject a confirmed transfer");
        }
        
        // Estorna o valor para a carteira origem com lock pessimista
        Wallet fromWallet = walletRepository.findByIdWithLock(pixTransfer.getFromWalletId())
                .orElseThrow(() -> new ApplicationException("Source wallet not found: " + 
                        pixTransfer.getFromWalletId()));
        
        fromWallet.deposit(pixTransfer.getAmount());
        walletRepository.save(fromWallet);
        
        // Registra transação de estorno
        Transaction refundTransaction = Transaction.deposit(
                pixTransfer.getFromWalletId(), 
                pixTransfer.getAmount(), 
                fromWallet.getBalance(), 
                "REFUND-" + pixTransfer.getEndToEndId());
        transactionRepository.save(refundTransaction);
        
        // Atualiza status da transferência
        pixTransfer.reject("Transfer rejected by payment system");
        pixTransferRepository.save(pixTransfer);
        
        log.info("Transfer rejected and refunded successfully: endToEndId={}", pixTransfer.getEndToEndId());
    }
}
