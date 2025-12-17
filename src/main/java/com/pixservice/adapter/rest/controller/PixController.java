package com.pixservice.adapter.rest.controller;

import com.pixservice.adapter.rest.dto.PixTransferRequest;
import com.pixservice.adapter.rest.dto.PixTransferResponse;
import com.pixservice.adapter.rest.dto.PixWebhookRequest;
import com.pixservice.application.usecase.PixTransferUseCase;
import com.pixservice.application.usecase.ProcessPixWebhookUseCase;
import com.pixservice.domain.entity.PixTransfer;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller para operações de Pix
 * Implementa fluxo completo de transferências Pix com state machine
 */
@RestController
@RequestMapping("/pix")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Pix", description = "APIs para transferências Pix e processamento de webhooks")
public class PixController {
    
    private final PixTransferUseCase pixTransferUseCase;
    private final ProcessPixWebhookUseCase processPixWebhookUseCase;
    
    @PostMapping("/transfers")
    @Operation(
        summary = "Realizar transferência Pix",
        description = """
            Inicia uma transferência Pix entre carteiras. A transferência é criada em estado PENDING 
            e deve ser confirmada via webhook. Valida saldo suficiente e debita imediatamente da 
            carteira de origem. Operação idempotente usando Idempotency-Key.
            """
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Transferência iniciada com sucesso",
            content = @Content(schema = @Schema(implementation = PixTransferResponse.class))),
        @ApiResponse(responseCode = "400", description = "Saldo insuficiente ou requisição inválida"),
        @ApiResponse(responseCode = "404", description = "Carteira ou chave Pix não encontrada"),
        @ApiResponse(responseCode = "409", description = "Transferência duplicada (mesma Idempotency-Key)")
    })
    public ResponseEntity<PixTransferResponse> transfer(
            @Valid @RequestBody
            @Parameter(description = "Dados da transferência Pix", required = true)
            PixTransferRequest request,
            @RequestHeader("Idempotency-Key")
            @Parameter(description = "Chave de idempotência única", required = true)
            String idempotencyKey) {
        
        log.info("POST /pix/transfers - from: {}, to: {}, amount: {}, idempotencyKey: {}", 
                request.fromWalletId(), request.toPixKey(), request.amount(), idempotencyKey);
        
        PixTransfer pixTransfer = pixTransferUseCase.execute(
                request.fromWalletId(),
                request.toPixKey(),
                request.amount(),
                idempotencyKey
        );
        
        PixTransferResponse response = new PixTransferResponse(
                pixTransfer.getEndToEndId(),
                pixTransfer.getStatus(),
                pixTransfer.getAmount(),
                pixTransfer.getToPixKey(),
                pixTransfer.getCreatedAt()
        );
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/webhook")
    @Operation(
        summary = "Processar webhook de confirmação/rejeição",
        description = """
            Processa eventos de webhook do sistema Pix para confirmar ou rejeitar transferências.
            Eventos duplicados são tratados de forma idempotente. Ao confirmar, credita o valor 
            na carteira de destino. Ao rejeitar, estorna o valor para a carteira de origem.
            """
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Webhook processado com sucesso"),
        @ApiResponse(responseCode = "400", description = "Requisição inválida"),
        @ApiResponse(responseCode = "404", description = "Transferência não encontrada"),
        @ApiResponse(responseCode = "409", description = "Evento duplicado ou estado inválido")
    })
    public ResponseEntity<Void> webhook(
            @Valid @RequestBody
            @Parameter(description = "Dados do evento de webhook", required = true)
            PixWebhookRequest request) {
        
        log.info("POST /pix/webhook - eventId: {}, endToEndId: {}, eventType: {}", 
                request.eventId(), request.endToEndId(), request.eventType());
        
        processPixWebhookUseCase.execute(
                request.eventId(),
                request.endToEndId(),
                request.eventType(),
                request.occurredAt()
        );
        
        return ResponseEntity.ok().build();
    }
}
