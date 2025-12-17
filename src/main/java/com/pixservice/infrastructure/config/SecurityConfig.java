package com.pixservice.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

/**
 * Configuração de segurança seguindo OWASP Top 10
 * Implementa headers de segurança e políticas de proteção
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    /**
     * Configura a cadeia de filtros de segurança com headers recomendados pelo OWASP
     * 
     * @param http Configurador HTTP de segurança
     * @return Cadeia de filtros configurada
     * @throws Exception Se houver erro na configuração
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF habilitado para endpoints críticos
            // APIs REST podem usar outros mecanismos como JWT em produção
            // Desabilitado apenas para endpoints de documentação e health check
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(
                    "/actuator/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                )
            )
            
            // Configuração de headers de segurança
            .headers(headers -> headers
                // X-Content-Type-Options: nosniff - Previne MIME type sniffing
                // Habilitado por padrão, não precisa de configuração explícita
                .contentTypeOptions(contentTypeOptions -> {})
                
                // X-Frame-Options: DENY - Previne clickjacking
                .frameOptions(frameOptions -> frameOptions.deny())
                
                // X-XSS-Protection: 1; mode=block - Proteção contra XSS (legado, mas ainda útil)
                .xssProtection(xss -> xss
                    .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                
                // Referrer-Policy: no-referrer - Controla informações de referrer
                .referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                
                // Content-Security-Policy - Previne XSS e injeção de código
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; frame-ancestors 'none'"))
                
                // HTTP Strict-Transport-Security - Força HTTPS
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)) // 1 ano
            )
            
            // Configuração de autorização
            .authorizeHttpRequests(auth -> auth
                // Permite acesso aos endpoints de documentação e health check
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/info",
                    "/actuator/prometheus",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                
                // Todos os outros endpoints requerem autenticação
                // Em produção, adicionar autenticação JWT/OAuth2
                .anyRequest().permitAll() // Permitindo para desenvolvimento
            );
        
        return http.build();
    }
}
