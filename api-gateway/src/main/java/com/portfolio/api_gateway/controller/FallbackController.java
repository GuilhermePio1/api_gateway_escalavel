package com.portfolio.api_gateway.controller;

import com.portfolio.api_gateway.exception.ServiceUnavailableException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Controller de fallback para quando o CircuitBreaker esta aberto
 * ou o serviço downstream esta indisponível.
 *
 * Lança {@link ServiceUnavailableException} que é tratada centralmente
 * pelo {@link com.portfolio.api_gateway.filter.error.GlobalErrorFilter},
 * garantindo resposta padronizada via {@link com.portfolio.api_gateway.dto.ErrorResponse}.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/orders")
    public Mono<Void> ordersFallback() {
        return Mono.error(new ServiceUnavailableException("orders-service"));
    }

    @GetMapping("/users")
    public Mono<Void> usersFallback() {
        return Mono.error(new ServiceUnavailableException("users-service"));
    }

    @GetMapping("/products")
    public Mono<Void> productsFallback() {
        return Mono.error(new ServiceUnavailableException("products-service"));
    }
}