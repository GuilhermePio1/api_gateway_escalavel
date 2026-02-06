package com.portfolio.api_gateway.ratelimit;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Política de rate limiting configurável.
 * Define o numero máximo de requisições permitidas dentro de uma janela de tempo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitPolicy {

    /** Numero máximo de requisições permitidas na janela. */
    @Min(1)
    private long maxRequests;

    /** Duração da janela em milissegundos. */
    @Min(1)
    private long windowMs;

    public long getWindowMillis() {
        return windowMs;
    }
}
