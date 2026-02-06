package com.portfolio.api_gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Controller de fallback para quando o CircuitBreaker esta aberto
 * ou o serviço downstream esta indisponível.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping(value = "/orders", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> ordersFallback() {
        return buildFallbackResponse("orders-service");
    }

    @GetMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> usersFallback() {
        return buildFallbackResponse("users-service");
    }

    @GetMapping(value = "/products", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> productsFallback() {
        return buildFallbackResponse("products-service");
    }

    private Mono<ResponseEntity<Map<String, Object>>> buildFallbackResponse(String serviceName) {
        Map<String, Object> body = Map.of(
                "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "error", "Service Unavailable",
                "message", "O servico " + serviceName + " esta temporariamente indisponivel. Tente novamente em alguns instantes.",
                "service", serviceName,
                "timestamp", Instant.now().toString()
        );

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE) // Define o Status HTTP real como 503
                .header("X-Fallback-Response", "true") // Header útil para debug/logs
                .body(body));
    }
}
