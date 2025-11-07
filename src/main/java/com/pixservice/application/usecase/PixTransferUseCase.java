package com.pixservice.application.usecase;

import com.pixservice.application.exception.ApplicationException;
import com.pixservice.domain.entity.PixKey;
import com.pixservice.domain.entity.PixTransfer;
import com.pixservice.domain.entity.Transaction;
import com.pixservice.domain.entity.Wallet;
import com.pixservice.domain.enums.PixTransferStatus;
import com.pixservice.infrastructure.repository.PixKeyRepository;
import com.pixservice.infrastructure.repository.PixTransferRepository;
import com.pixservice.infrastructure.repository.TransactionRepository;
import com.pixservice.infrastructure.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Use Case: Transferência Pix
 * Processa transferência entre carteiras com garantia de exactly-once
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PixTransferUseCase {
    
    private final WalletRepository walletRepository;
    private final PixKeyRepository pixKeyRepository;
    private final PixTransferRepository pixTransferRepository;
    private final TransactionRepository transactionRepository;
    
    @Transactional
    public PixTransfer execute(UUID fromWalletId, String toPixKey, BigDecimal amount, String idempotencyKey) {
        log.info("Processando transferência Pix: fromWallet={}, toPixKey={}, amount={}, idempotencyKey={}", 
                fromWalletId, toPixKey, amount, idempotencyKey);
        
        // Validações
        validateTransferRequest(fromWalletId, toPixKey, amount, idempotencyKey);
        
        // Verifica idempotência
        if (pixTransferRepository.existsByIdempotencyKey(idempotencyKey)) {
            PixTransfer existingTransfer = pixTransferRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow();
            log.info("Requisição de transferência duplicada detectada, retornando transferência existente: {}", 
                    existingTransfer.getEndToEndId());
            return existingTransfer;
        }
        
        // Busca chave Pix de destino
        PixKey destinationPixKey = pixKeyRepository.findByKeyValue(toPixKey)
                .orElseThrow(() -> new ApplicationException("Chave Pix não encontrada: " + toPixKey));
        
        UUID toWalletId = destinationPixKey.getWallet().getId();
        
        // Validação: não pode transferir para si mesmo
        if (fromWalletId.equals(toWalletId)) {
            throw new ApplicationException("Não é possível transferir para a mesma carteira");
        }
        
        // Busca carteira origem com lock pessimista
        Wallet fromWallet = walletRepository.findByIdWithLock(fromWalletId)
                .orElseThrow(() -> new ApplicationException("Carteira de origem não encontrada: " + fromWalletId));
        
        // Valida saldo
        if (!fromWallet.hasSufficientBalance(amount)) {
            throw new ApplicationException("Saldo insuficiente. Saldo atual: " + fromWallet.getBalance() + 
                    ", Necessário: " + amount);
        }
        
        // Gera endToEndId único
        String endToEndId = generateEndToEndId();
        
        // Realiza débito na carteira origem
        fromWallet.withdraw(amount);
        walletRepository.save(fromWallet);
        
        // Registra transação de saída
        Transaction outTransaction = Transaction.pixTransferOut(
                fromWalletId, amount, fromWallet.getBalance(), endToEndId, idempotencyKey, toPixKey);
        transactionRepository.save(outTransaction);
        
        // Cria registro da transferência Pix em estado PENDING
        PixTransfer pixTransfer = PixTransfer.builder()
                .endToEndId(endToEndId)
                .idempotencyKey(idempotencyKey)
                .fromWalletId(fromWalletId)
                .toPixKey(toPixKey)
                .toWalletId(toWalletId)
                .amount(amount)
                .status(PixTransferStatus.PENDING)
                .build();
        
        PixTransfer savedTransfer = pixTransferRepository.save(pixTransfer);
        
        log.info("Pix transfer initiated successfully: endToEndId={}, status={}", 
                endToEndId, savedTransfer.getStatus());
        
        return savedTransfer;
    }
    
    private void validateTransferRequest(UUID fromWalletId, String toPixKey, 
                                        BigDecimal amount, String idempotencyKey) {
        if (fromWalletId == null) {
            throw new IllegalArgumentException("Source wallet ID is required");
        }
        if (toPixKey == null || toPixKey.isBlank()) {
            throw new IllegalArgumentException("Destination Pix key is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
    }
    
    private String generateEndToEndId() {
        // Formato simplificado: E + timestamp + UUID
        return "E" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
