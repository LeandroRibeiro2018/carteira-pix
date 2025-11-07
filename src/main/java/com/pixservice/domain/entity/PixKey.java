package com.pixservice.domain.entity;

import com.pixservice.domain.enums.PixKeyType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidade PixKey - Chave Pix associada a uma carteira
 */
@Entity
@Table(name = "pix_keys", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"key_type", "key_value"})
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PixKey {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "key_type", nullable = false, length = 20)
    private PixKeyType keyType;
    
    @Column(name = "key_value", nullable = false, unique = true)
    private String keyValue;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    // Domain validation
    public void validate() {
        if (keyType == null || keyValue == null || keyValue.isBlank()) {
            throw new IllegalArgumentException("Tipo e valor da chave Pix são obrigatórios");
        }
        
        switch (keyType) {
            case EMAIL -> validateEmail(keyValue);
            case PHONE -> validatePhone(keyValue);
            case CPF -> validateCpf(keyValue);
            case EVP -> validateEvp(keyValue);
        }
    }
    
    private void validateEmail(String email) {
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new IllegalArgumentException("Formato de email inválido");
        }
    }
    
    private void validatePhone(String phone) {
        String cleanPhone = phone.replaceAll("[^0-9]", "");
        if (cleanPhone.length() < 10 || cleanPhone.length() > 11) {
            throw new IllegalArgumentException("Formato de telefone inválido (deve ter 10 ou 11 dígitos)");
        }
    }
    
    private void validateCpf(String cpf) {
        String cleanCpf = cpf.replaceAll("[^0-9]", "");
        if (cleanCpf.length() != 11) {
            throw new IllegalArgumentException("Formato de CPF inválido (deve ter 11 dígitos)");
        }
    }
    
    private void validateEvp(String evp) {
        if (!evp.matches("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$")) {
            throw new IllegalArgumentException("Formato de chave aleatória inválido (deve ser UUID)");
        }
    }
}
