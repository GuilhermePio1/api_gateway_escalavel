package com.portfolio.api_gateway.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Exceção lançada quando um serviço downstream está indisponível.
 * Utilizada pelo FallbackController quando o CircuitBreaker está aberto
 * ou quando não há instâncias saudáveis no service discovery.
 */
@Getter
public class ServiceUnavailableException extends GatewayException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String serviceName;

    public ServiceUnavailableException(String serviceName) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                "O servico " + serviceName + " esta temporariamente indisponivel. Tente novamente em alguns instantes.");
        this.serviceName = serviceName;
    }

    public ServiceUnavailableException(String serviceName, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                "O servico " + serviceName + " esta temporariamente indisponivel. Tente novamente em alguns instantes.",
                cause);
        this.serviceName = serviceName;
    }
}
