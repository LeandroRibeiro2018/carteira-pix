package com.pixservice.adapter.rest.dto;

import com.pixservice.domain.enums.WebhookEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * DTO para webhook de Pix
 * 
 * @param eventId ID único do evento de webhook
 * @param endToEndId End-to-end ID da transferência Pix
 * @param eventType Tipo do evento (CONFIRMED ou REJECTED)
 * @param occurredAt Timestamp de quando o evento ocorreu
 */
public record PixWebhookRequest(
    @NotBlank(message = "Event ID is required")
    String eventId,
    
    @NotBlank(message = "End-to-end ID is required")
    String endToEndId,
    
    @NotNull(message = "Event type is required")
    WebhookEventType eventType,
    
    @NotNull(message = "Occurred timestamp is required")
    Instant occurredAt
) {}
