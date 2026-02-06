package com.portfolio.api_gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;
import java.security.Principal;

/**
 * Configuração do KeyResolver para o Rate Limiter do Spring Cloud Gateway.
 *
 * O bean "userKeyResolver" é referenciado nas rotas YAML via SpEL:
 * key-resolver: "#{@userKeyResolver}"
 *
 * Estratégia de resolução de chave:
 * 1. User ID extraído do JWT (Principal name) para requisições autenticadas
 * 2. Fallback para IP do cliente quando não há autenticação
 */
@Configuration
public class RateLimiterKeyResolverConfig {

    // Resolver nativo do Spring para lidar com X-Forwarded-For
    private final XForwardedRemoteAddressResolver ipResolver = XForwardedRemoteAddressResolver.maxTrustedIndex(1);

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .defaultIfEmpty(getClientIp(exchange));
    }

    private String getClientIp(ServerWebExchange exchange) {
        // Tenta resolver o IP considerando proxies (X-Forwarded-For)
        InetSocketAddress address = ipResolver.resolve(exchange);

        if (address != null && address.getAddress() != null) {
            return address.getAddress().getHostAddress();
        }

        // Fallback final se não conseguir resolver nada
        return "anonymous";
    }
}
