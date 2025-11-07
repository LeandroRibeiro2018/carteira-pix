package com.pixservice.domain.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Testes unitários para entidade Wallet
 */
class WalletTest {
    
    @Test
    void shouldCreateWalletWithZeroBalance() {
        Wallet wallet = Wallet.builder()
                .userId("user123")
                .build();
        
        assertThat(wallet.getBalance()).isEqualTo(BigDecimal.ZERO);
    }
    
    @Test
    void shouldDepositSuccessfully() {
        Wallet wallet = Wallet.builder()
                .userId("user123")
                .balance(BigDecimal.valueOf(100))
                .build();
        
        wallet.deposit(BigDecimal.valueOf(50));
        
        assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(150));
    }
    
    @Test
    void shouldThrowExceptionWhenDepositingZeroOrNegative() {
        Wallet wallet = Wallet.builder().userId("user123").build();
        
        assertThatThrownBy(() -> wallet.deposit(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount must be greater than zero");
        
        assertThatThrownBy(() -> wallet.deposit(BigDecimal.valueOf(-10)))
                .isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    void shouldWithdrawSuccessfully() {
        Wallet wallet = Wallet.builder()
                .userId("user123")
                .balance(BigDecimal.valueOf(100))
                .build();
        
        wallet.withdraw(BigDecimal.valueOf(30));
        
        assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(70));
    }
    
    @Test
    void shouldThrowExceptionWhenInsufficientBalance() {
        Wallet wallet = Wallet.builder()
                .userId("user123")
                .balance(BigDecimal.valueOf(50))
                .build();
        
        assertThatThrownBy(() -> wallet.withdraw(BigDecimal.valueOf(100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient balance");
    }
    
    @Test
    void shouldCheckSufficientBalance() {
        Wallet wallet = Wallet.builder()
                .userId("user123")
                .balance(BigDecimal.valueOf(100))
                .build();
        
        assertThat(wallet.hasSufficientBalance(BigDecimal.valueOf(50))).isTrue();
        assertThat(wallet.hasSufficientBalance(BigDecimal.valueOf(100))).isTrue();
        assertThat(wallet.hasSufficientBalance(BigDecimal.valueOf(150))).isFalse();
    }
}
