package com.portfolio.api_gateway.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Exceção base para todas as exceções customizadas do API Gateway.
 * Encapsula o status HTTP, um código de erro identificável e a mensagem descritiva.
 */
@Getter
public class GatewayException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String errorCode;

    public GatewayException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public GatewayException(HttpStatus status, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }
}
