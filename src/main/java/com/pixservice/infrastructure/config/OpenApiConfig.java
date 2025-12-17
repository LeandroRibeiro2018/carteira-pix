package com.pixservice.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuração do OpenAPI/Swagger para documentação da API
 * Segue padrões enterprise de documentação de APIs REST
 */
@Configuration
public class OpenApiConfig {
    
    @Value("${spring.application.name}")
    private String applicationName;
    
    /**
     * Configura a documentação OpenAPI 3.0 da aplicação
     * 
     * @return Configuração do OpenAPI com informações da API
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Pix Wallet Service API")
                        .version("1.0.0")
                        .description("""
                                API RESTful para gerenciamento de carteira digital com suporte a transferências Pix.
                                
                                ## Funcionalidades Principais
                                - Criação e gerenciamento de carteiras digitais
                                - Registro de chaves Pix (CPF, EMAIL, PHONE, etc.)
                                - Depósitos e saques com idempotência
                                - Transferências Pix com state machine (PENDING → CONFIRMED/REJECTED)
                                - Webhooks para confirmação/rejeição de transferências
                                - Consulta de saldo atual e histórico
                                - Ledger imutável de transações
                                
                                ## Segurança
                                - Validação rigorosa de inputs
                                - Idempotência em operações críticas (header: Idempotency-Key)
                                - Pessimistic locking para garantir consistência
                                - Exactly-once semantics em transferências
                                
                                ## Arquitetura
                                - Clean Architecture / Hexagonal Architecture
                                - Domain-Driven Design (DDD)
                                - CQRS pattern para consultas
                                - Event-driven via webhooks
                                """)
                        .contact(new Contact()
                                .name("API Support")
                                .email("support@pixwallet.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development Server"),
                        new Server()
                                .url("https://api.pixwallet.com")
                                .description("Production Server")
                ));
    }
}
