package com.pixservice.adapter.rest.controller;

import com.pixservice.adapter.rest.dto.*;
import com.pixservice.application.usecase.*;
import com.pixservice.domain.entity.PixKey;
import com.pixservice.domain.entity.Transaction;
import com.pixservice.domain.entity.Wallet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * Implementa endpoints REST seguindo princípios RESTful e OpenAPI 3.0
 */
@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Wallet", description = "APIs para gerenciamento de carteiras digitais")
public class WalletController {
    
    private final CreateWalletUseCase createWalletUseCase;
    private final RegisterPixKeyUseCase registerPixKeyUseCase;
    private final GetBalanceUseCase getBalanceUseCase;
    private final DepositUseCase depositUseCase;
    private final WithdrawUseCase withdrawUseCase;
    
    @PostMapping
    @Operation(
        summary = "Criar nova carteira",
        description = "Cria uma nova carteira digital para um usuário. Cada usuário pode ter apenas uma carteira."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Carteira criada com sucesso",
            content = @Content(schema = @Schema(implementation = WalletResponse.class))),
        @ApiResponse(responseCode = "400", description = "Requisição inválida"),
        @ApiResponse(responseCode = "409", description = "Usuário já possui carteira")
    })
    public ResponseEntity<WalletResponse> createWallet(
            @Valid @RequestBody 
            @Parameter(description = "Dados para criar a carteira", required = true)
            CreateWalletRequest request) {
        
        log.info("POST /wallets - userId: {}", request.userId());
        
        Wallet wallet = createWalletUseCase.execute(request.userId());
        
        WalletResponse response = new WalletResponse(
                wallet.getId(),
                wallet.getUserId(),
                wallet.getBalance(),
                wallet.getCreatedAt()
        );
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/{id}/pix-keys")
    @Operation(
        summary = "Registrar chave Pix",
        description = "Registra uma nova chave Pix para a carteira. Cada chave deve ser única no sistema."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Chave Pix registrada com sucesso",
            content = @Content(schema = @Schema(implementation = PixKeyResponse.class))),
        @ApiResponse(responseCode = "400", description = "Requisição inválida"),
        @ApiResponse(responseCode = "404", description = "Carteira não encontrada"),
        @ApiResponse(responseCode = "409", description = "Chave Pix já cadastrada")
    })
    public ResponseEntity<PixKeyResponse> registerPixKey(
            @PathVariable 
            @Parameter(description = "ID da carteira", required = true)
            UUID id,
            @Valid @RequestBody
            @Parameter(description = "Dados da chave Pix", required = true)
            RegisterPixKeyRequest request) {
        
        log.info("POST /wallets/{}/pix-keys - type: {}, value: {}", id, request.keyType(), request.keyValue());
        
        PixKey pixKey = registerPixKeyUseCase.execute(id, request.keyType(), request.keyValue());
        
        PixKeyResponse response = new PixKeyResponse(
                pixKey.getId(),
                pixKey.getKeyType(),
                pixKey.getKeyValue(),
                pixKey.getWallet().getId(),
                pixKey.getCreatedAt()
        );
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/{id}/balance")
    @Operation(
        summary = "Consultar saldo",
        description = "Consulta o saldo atual ou histórico de uma carteira. " +
                     "Suporta consulta point-in-time com o parâmetro 'at'."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Saldo consultado com sucesso",
            content = @Content(schema = @Schema(implementation = BalanceResponse.class))),
        @ApiResponse(responseCode = "404", description = "Carteira não encontrada")
    })
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable 
            @Parameter(description = "ID da carteira", required = true)
            UUID id,
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Parameter(description = "Data/hora para consulta histórica (opcional)")
            Instant at) {
        
        log.info("GET /wallets/{}/balance - at: {}", id, at);
        
        java.math.BigDecimal balance;
        if (at != null) {
            balance = getBalanceUseCase.getHistoricalBalance(id, at);
        } else {
            balance = getBalanceUseCase.getCurrentBalance(id);
        }
        
        BalanceResponse response = new BalanceResponse(balance);
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{id}/deposit")
    @Operation(
        summary = "Realizar depósito",
        description = "Adiciona saldo à carteira. Operação idempotente usando Idempotency-Key."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Depósito realizado com sucesso",
            content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
        @ApiResponse(responseCode = "400", description = "Requisição inválida"),
        @ApiResponse(responseCode = "404", description = "Carteira não encontrada")
    })
    public ResponseEntity<TransactionResponse> deposit(
            @PathVariable 
            @Parameter(description = "ID da carteira", required = true)
            UUID id,
            @Valid @RequestBody
            @Parameter(description = "Dados do depósito", required = true)
            DepositRequest request,
            @RequestHeader("Idempotency-Key")
            @Parameter(description = "Chave de idempotência única", required = true)
            String idempotencyKey) {
        
        log.info("POST /wallets/{}/deposit - amount: {}, idempotencyKey: {}", id, request.amount(), idempotencyKey);
        
        Transaction transaction = depositUseCase.execute(id, request.amount(), idempotencyKey);
        
        TransactionResponse response = mapTransactionToResponse(transaction);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/{id}/withdraw")
    @Operation(
        summary = "Realizar saque",
        description = "Remove saldo da carteira. Valida saldo suficiente. Operação idempotente."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Saque realizado com sucesso",
            content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
        @ApiResponse(responseCode = "400", description = "Saldo insuficiente ou requisição inválida"),
        @ApiResponse(responseCode = "404", description = "Carteira não encontrada")
    })
    public ResponseEntity<TransactionResponse> withdraw(
            @PathVariable 
            @Parameter(description = "ID da carteira", required = true)
            UUID id,
            @Valid @RequestBody
            @Parameter(description = "Dados do saque", required = true)
            WithdrawRequest request,
            @RequestHeader("Idempotency-Key")
            @Parameter(description = "Chave de idempotência única", required = true)
            String idempotencyKey) {
        
        log.info("POST /wallets/{}/withdraw - amount: {}, idempotencyKey: {}", id, request.amount(), idempotencyKey);
        
        Transaction transaction = withdrawUseCase.execute(id, request.amount(), idempotencyKey);
        
        TransactionResponse response = mapTransactionToResponse(transaction);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    private TransactionResponse mapTransactionToResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getWalletId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getDescription(),
                transaction.getEndToEndId(),
                transaction.getCreatedAt()
        );
    }
}
