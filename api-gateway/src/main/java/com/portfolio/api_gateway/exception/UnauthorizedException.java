package com.portfolio.api_gateway.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Exceção lançada quando a autenticação falha ou o token JWT é inválido/expirado.
 * Pode ser utilizada em filtros customizados para rejeitar requisições não autenticadas.
 */
public class UnauthorizedException extends GatewayException {

    @Serial
    private static final long serialVersionUID = 1L;

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message, cause);
    }
}
