package com.pixservice.adapter.rest.controller;

import com.pixservice.adapter.rest.dto.PixTransferRequest;
import com.pixservice.adapter.rest.dto.PixTransferResponse;
import com.pixservice.adapter.rest.dto.PixWebhookRequest;
import com.pixservice.application.usecase.PixTransferUseCase;
import com.pixservice.application.usecase.ProcessPixWebhookUseCase;
import com.pixservice.domain.entity.PixTransfer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller para operações de Pix
 */
@RestController
@RequestMapping("/pix")
@RequiredArgsConstructor
@Slf4j
public class PixController {
    
    private final PixTransferUseCase pixTransferUseCase;
    private final ProcessPixWebhookUseCase processPixWebhookUseCase;
    
    @PostMapping("/transfers")
    public ResponseEntity<PixTransferResponse> transfer(
            @Valid @RequestBody PixTransferRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        
        log.info("POST /pix/transfers - from: {}, to: {}, amount: {}, idempotencyKey: {}", 
                request.getFromWalletId(), request.getToPixKey(), request.getAmount(), idempotencyKey);
        
        PixTransfer pixTransfer = pixTransferUseCase.execute(
                request.getFromWalletId(),
                request.getToPixKey(),
                request.getAmount(),
                idempotencyKey
        );
        
        PixTransferResponse response = PixTransferResponse.builder()
                .endToEndId(pixTransfer.getEndToEndId())
                .status(pixTransfer.getStatus())
                .amount(pixTransfer.getAmount())
                .toPixKey(pixTransfer.getToPixKey())
                .createdAt(pixTransfer.getCreatedAt())
                .build();
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@Valid @RequestBody PixWebhookRequest request) {
        log.info("POST /pix/webhook - eventId: {}, endToEndId: {}, eventType: {}", 
                request.getEventId(), request.getEndToEndId(), request.getEventType());
        
        processPixWebhookUseCase.execute(
                request.getEventId(),
                request.getEndToEndId(),
                request.getEventType(),
                request.getOccurredAt()
        );
        
        return ResponseEntity.ok().build();
    }
}
