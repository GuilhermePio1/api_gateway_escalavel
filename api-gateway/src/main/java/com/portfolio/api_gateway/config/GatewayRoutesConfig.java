package com.portfolio.api_gateway.config;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.Buildable;
import org.springframework.cloud.gateway.route.builder.PredicateSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import java.time.Duration;

/**
 * Rotas programáticas complementares as rotas YAML.
 *
 * As rotas YAML (application.yaml) definem os endpoints públicos da API (/api/v1/**).
 * Esta classe define rotas programáticas para endpoints internos (/api/v1/.../internal/**)
 * que possuem configurações especificas de resiliência e timeout diferenciadas.
 */
@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator programmaticRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("orders-service-internal", r -> commonInternalRoute(r,
                        "orders-service", "/api/v1/orders/internal/**", 3))

                .route("users-service-internal", r -> commonInternalRoute(r,
                        "users-service", "/api/v1/users/internal/**", 2)) // Retries diferenciados

                .route("products-service-internal", r -> commonInternalRoute(r,
                        "products-service", "/api/v1/products/internal/**", 3))
                .build();
    }

    /**
     * Método auxiliar para padronizar as rotas internas.
     * Aplica: StripPrefix, Headers, CircuitBreaker, Retry e (Novo) Bulkhead.
     */
    private Buildable<Route> commonInternalRoute(
            PredicateSpec r,
            String serviceName,
            String pathPattern,
            int retryAttempts) {

        return r.path(pathPattern)
                .and()
                .method(HttpMethod.GET, HttpMethod.POST)
                .filters(f -> f
                        .stripPrefix(3) // Remove /api/v1/{service}
                        .addRequestHeader("X-Service-Name", serviceName)
                        .addRequestHeader("X-Internal-Request", "true")

                        // 1. Circuit Breaker (Mantido)
                        .circuitBreaker(cb -> cb
                                .setName(serviceName)
                                .setFallbackUri("forward:/fallback/" + serviceName.split("-")[0]) // ex: orders
                        )

                        // 2. Retry (Mantido)
                        .retry(retryConfig -> retryConfig
                                .setRetries(retryAttempts)
                                .setMethods(HttpMethod.GET)
                                .setBackoff(Duration.ofMillis(100), Duration.ofMillis(500), 2, false)
                        )

                )
                .uri("lb://" + serviceName);

    }
}
