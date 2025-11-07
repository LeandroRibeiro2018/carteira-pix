package com.pixservice.application.exception;

/**
 * Exceções customizadas da aplicação
 */
public class ApplicationException extends RuntimeException {
    
    public ApplicationException(String message) {
        super(message);
    }
    
    public ApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}

class WalletNotFoundException extends ApplicationException {
    public WalletNotFoundException(String message) {
        super(message);
    }
}

class InsufficientBalanceException extends ApplicationException {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}

class PixKeyAlreadyExistsException extends ApplicationException {
    public PixKeyAlreadyExistsException(String message) {
        super(message);
    }
}

class PixKeyNotFoundException extends ApplicationException {
    public PixKeyNotFoundException(String message) {
        super(message);
    }
}

class InvalidPixTransferException extends ApplicationException {
    public InvalidPixTransferException(String message) {
        super(message);
    }
}

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
