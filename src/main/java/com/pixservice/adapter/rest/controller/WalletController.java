package com.pixservice.adapter.rest.controller;

import com.pixservice.adapter.rest.dto.*;
import com.pixservice.application.usecase.*;
import com.pixservice.domain.entity.PixKey;
import com.pixservice.domain.entity.Transaction;
import com.pixservice.domain.entity.Wallet;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Controller para operações de carteira
 */
@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
@Slf4j
public class WalletController {
    
    private final CreateWalletUseCase createWalletUseCase;
    private final RegisterPixKeyUseCase registerPixKeyUseCase;
    private final GetBalanceUseCase getBalanceUseCase;
    private final DepositUseCase depositUseCase;
    private final WithdrawUseCase withdrawUseCase;
    
    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(@Valid @RequestBody CreateWalletRequest request) {
        log.info("POST /wallets - userId: {}", request.getUserId());
        
        Wallet wallet = createWalletUseCase.execute(request.getUserId());
        
        WalletResponse response = WalletResponse.builder()
                .id(wallet.getId())
                .userId(wallet.getUserId())
                .balance(wallet.getBalance())
                .createdAt(wallet.getCreatedAt())
                .build();
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/{id}/pix-keys")
    public ResponseEntity<PixKeyResponse> registerPixKey(
            @PathVariable UUID id,
            @Valid @RequestBody RegisterPixKeyRequest request) {
        
        log.info("POST /wallets/{}/pix-keys - type: {}, value: {}", id, request.getKeyType(), request.getKeyValue());
        
        PixKey pixKey = registerPixKeyUseCase.execute(id, request.getKeyType(), request.getKeyValue());
        
        PixKeyResponse response = PixKeyResponse.builder()
                .id(pixKey.getId())
                .keyType(pixKey.getKeyType())
                .keyValue(pixKey.getKeyValue())
                .walletId(pixKey.getWallet().getId())
                .createdAt(pixKey.getCreatedAt())
                .build();
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at) {
        
        log.info("GET /wallets/{}/balance - at: {}", id, at);
        
        java.math.BigDecimal balance;
        if (at != null) {
            balance = getBalanceUseCase.getHistoricalBalance(id, at);
        } else {
            balance = getBalanceUseCase.getCurrentBalance(id);
        }
        
        BalanceResponse response = BalanceResponse.builder()
                .balance(balance)
                .build();
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{id}/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @PathVariable UUID id,
            @Valid @RequestBody DepositRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        
        log.info("POST /wallets/{}/deposit - amount: {}, idempotencyKey: {}", id, request.getAmount(), idempotencyKey);
        
        Transaction transaction = depositUseCase.execute(id, request.getAmount(), idempotencyKey);
        
        TransactionResponse response = mapTransactionToResponse(transaction);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/{id}/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(
            @PathVariable UUID id,
            @Valid @RequestBody WithdrawRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        
        log.info("POST /wallets/{}/withdraw - amount: {}, idempotencyKey: {}", id, request.getAmount(), idempotencyKey);
        
        Transaction transaction = withdrawUseCase.execute(id, request.getAmount(), idempotencyKey);
        
        TransactionResponse response = mapTransactionToResponse(transaction);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    private TransactionResponse mapTransactionToResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .walletId(transaction.getWalletId())
                .type(transaction.getType())
                .amount(transaction.getAmount())
                .balanceAfter(transaction.getBalanceAfter())
                .description(transaction.getDescription())
                .endToEndId(transaction.getEndToEndId())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
