package com.portfolio.api_gateway.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

/**
 * Implementação do rate limiter distribuído usando Redis com algoritmo Sliding Window Log.
 *
 * Utiliza um SortedSet no Redis onde:
 * - Cada membro é um identificador único da requisição (timestamp:random)
 * - O score e o timestamp em milissegundos da requisição
 *
 * Um script Lua garante atomicidade das operações:
 * 1. Remove entradas expiradas (fora da janela)
 * 2. Conta requisições na janela atual
 * 3. Adiciona nova entrada se dentro do limite
 * 4. Define TTL para limpeza automática
 */
@Slf4j
@Service("customRedisRateLimiter")
@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiterService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final RedisScript<List<Long>> rateLimitScript;

    private static final String KEY_PREFIX = "rate_limit:";

    @Override
    public Mono<RateLimitResult> isAllowed(String key, RateLimitPolicy policy) {
        long now = Instant.now().toEpochMilli();
        String redisKey = KEY_PREFIX + key;
        long windowStart = now - policy.getWindowMillis();

        return redisTemplate.execute(
                rateLimitScript,
                List.of(redisKey),
                List.of(
                        String.valueOf(now),
                        String.valueOf(windowStart),
                        String.valueOf(policy.getMaxRequests()),
                        String.valueOf(policy.getWindowMillis())
                )
        )
                .next()
                .map(result -> {
                    long currentCount = result.get(0);
                    long ttl = result.get(1);
                    boolean allowed = currentCount <= policy.getMaxRequests();

                    return RateLimitResult.builder()
                            .allowed(allowed)
                            .limit(policy.getMaxRequests())
                            .remaining(Math.max(0, policy.getMaxRequests() - currentCount))
                            .resetAt(Instant.now().plusMillis(ttl))
                            .build();
                })
                .doOnError(e -> log.error("Erro ao executar rate limiting para key={}: {}", redisKey, e.getMessage()))
                .onErrorReturn(RateLimitResult.builder()
                        .allowed(true)
                        .limit(policy.getMaxRequests())
                        .remaining(policy.getMaxRequests())
                        .resetAt(Instant.now().plusMillis(policy.getWindowMillis()))
                        .build());
    }
}
