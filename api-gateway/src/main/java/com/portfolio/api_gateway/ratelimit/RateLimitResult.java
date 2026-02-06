package com.portfolio.api_gateway.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Resultado da verificação de rate limiting.
 * Contém as informações necessárias para popular os headers de resposta.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitResult {

    /** Se a requisição foi permitida. */
    private boolean allowed;

    /** Limite máximo de requisições na janela. */
    private long limit;

    /** Requisições restantes na janela atual. */
    private long remaining;

    /** Instante em que a janela expira (reset). */
    private Instant resetAt;
}
