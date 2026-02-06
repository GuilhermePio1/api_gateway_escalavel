package com.portfolio.api_gateway.ratelimit;

import reactor.core.publisher.Mono;

/**
 * Interface do serviço de rate limiting distribuído.
 */
public interface RateLimiterService {

    /**
     * Verifica se uma requisição é permitida dado a chave e a política de rate limiting.
     *
     * @param key    identificador único (user ID, IP, API key)
     * @param policy política de rate limiting a ser aplicada
     * @return resultado contendo se foi permitido, limite, remaining e reset
     */
    Mono<RateLimitResult> isAllowed(String key, RateLimitPolicy policy);
}
