package com.pixservice.adapter.rest.dto;

import com.pixservice.domain.enums.WebhookEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO para webhook de Pix
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PixWebhookRequest {
    
    @NotBlank(message = "Event ID is required")
    private String eventId;
    
    @NotBlank(message = "End-to-end ID is required")
    private String endToEndId;
    
    @NotNull(message = "Event type is required")
    private WebhookEventType eventType;
    
    @NotNull(message = "Occurred timestamp is required")
    private Instant occurredAt;
}
