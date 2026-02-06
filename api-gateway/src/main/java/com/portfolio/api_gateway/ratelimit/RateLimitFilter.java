package com.portfolio.api_gateway.ratelimit;

import com.portfolio.api_gateway.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Filtro global de rate limiting usando algoritmo Sliding Window Log com Redis.
 *
 * Posição na cadeia de filtros: order=2 (após logging, antes de validação).
 *
 * Fluxo:
 * 1. Extrai a chave identificadora via KeyResolver (User ID do JWT ou IP)
 * 2. Identifica a rota e seleciona a política de rate limiting correspondente
 * 3. Executa o script Lua atómico no Redis (Sliding Window)
 * 4. Se permitido: adiciona headers informativos e continua o chain
 * 5. Se bloqueado: retorna 429 Too Many Requests com Retry-After
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties properties;
    private final KeyResolver userKeyResolver;

    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) {
            return chain.filter(exchange);
        }

        String routeId = route.getId();
        RateLimitPolicy policy = resolvePolicy(routeId);

        return userKeyResolver.resolve(exchange)
                .flatMap(key -> {
                    String compositeKey = routeId + ":" + key;
                    return rateLimiterService.isAllowed(compositeKey, policy);
                })
                .flatMap(result -> {
                    ServerHttpResponse response = exchange.getResponse();

                    // Headers informativos sempre presentes
                    response.getHeaders().set("X-RateLimit-Limit", String.valueOf(result.getLimit()));
                    response.getHeaders().set("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
                    response.getHeaders().set("X-RateLimit-Reset", String.valueOf(result.getResetAt().toEpochMilli()));

                    if (result.isAllowed()) {
                        return chain.filter(exchange);
                    }

                    // Rate limit excedido: propaga exceção para o GlobalErrorFilter
                    long retryAfterSeconds = Math.max(1,
                            result.getResetAt().getEpochSecond() - Instant.now().getEpochSecond());

                    return Mono.error(new RateLimitExceededException(
                            result.getLimit(),
                            result.getRemaining(),
                            result.getResetAt(),
                            retryAfterSeconds
                    ));
                });
    }

    @Override
    public int getOrder() {
        return 2;
    }

    private RateLimitPolicy resolvePolicy(String routeId) {
        RateLimitProperties.PolicyConfig routePolicy = properties.getRoutePolicies().get(routeId);
        if (routePolicy != null) {
            return routePolicy.toPolicy();
        }
        return properties.getDefaultPolicy().toPolicy();
    }
}