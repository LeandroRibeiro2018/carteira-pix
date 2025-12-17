package com.pixservice.application.exception;

/**
 * Exceção base da aplicação seguindo princípios de Clean Code
 * Fornece hierarquia de exceções específicas do domínio
 */
public class ApplicationException extends RuntimeException {
    
    public ApplicationException(String message) {
        super(message);
    }
    
    public ApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Exceção lançada quando uma carteira não é encontrada
 */
class WalletNotFoundException extends ApplicationException {
    public WalletNotFoundException(String message) {
        super(message);
    }
}

/**
 * Exceção lançada quando o saldo é insuficiente para uma operação
 */
class InsufficientBalanceException extends ApplicationException {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}

/**
 * Exceção lançada quando uma chave Pix já existe no sistema
 */
class PixKeyAlreadyExistsException extends ApplicationException {
    public PixKeyAlreadyExistsException(String message) {
        super(message);
    }
}

/**
 * Exceção lançada quando uma chave Pix não é encontrada
 */
class PixKeyNotFoundException extends ApplicationException {
    public PixKeyNotFoundException(String message) {
        super(message);
    }
}

/**
 * Exceção lançada quando uma transferência Pix é inválida
 */
class InvalidPixTransferException extends ApplicationException {
    public InvalidPixTransferException(String message) {
        super(message);
    }
}

/**
 * Exceção lançada quando uma requisição duplicada é detectada (idempotência)
 * Mantém referência ao resultado anterior para retorno idempotente
 */
class DuplicateRequestException extends ApplicationException {
    private final Object previousResult;
    
    public DuplicateRequestException(String message, Object previousResult) {
        super(message);
        this.previousResult = previousResult;
    }
    
    public Object getPreviousResult() {
        return previousResult;
    }
}
