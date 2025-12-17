package com.pixservice.infrastructure.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Health Indicator customizado para verificar conectividade com o banco de dados
 * Implementa checagem proativa de saúde do sistema
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseHealthIndicator implements HealthIndicator {
    
    private final DataSource dataSource;
    
    /**
     * Verifica a saúde da conexão com o banco de dados
     * 
     * @return Status de saúde com detalhes da conexão
     */
    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(1)) {
                return Health.up()
                        .withDetail("database", "PostgreSQL")
                        .withDetail("validationQuery", "Connection is valid")
                        .build();
            } else {
                return Health.down()
                        .withDetail("database", "PostgreSQL")
                        .withDetail("error", "Connection validation failed")
                        .build();
            }
        } catch (SQLException e) {
            log.error("Database health check failed", e);
            return Health.down()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
