package com.pixservice.infrastructure.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Utilitário para buscar mensagens internacionalizadas
 */
@Component
@RequiredArgsConstructor
public class Messages {
    
    private final MessageSource messageSource;
    
    public String get(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
