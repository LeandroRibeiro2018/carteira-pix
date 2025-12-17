package com.pixservice.adapter.rest.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;
import java.util.Map;

/**
 * DTO padrão para respostas de erro
 * 
 * @param timestamp Data/hora do erro
 * @param status Código de status HTTP
 * @param error Tipo do erro
 * @param message Mensagem descritiva do erro
 * @param validationErrors Mapa de erros de validação (campo -> mensagem)
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    Map<String, String> validationErrors
) {}
