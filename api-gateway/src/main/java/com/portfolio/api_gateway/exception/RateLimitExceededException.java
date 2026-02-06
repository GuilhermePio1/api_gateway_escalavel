package com.portfolio.api_gateway.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.io.Serial;
import java.time.Instant;

/**
 * Exceção lançada quando o rate limit de uma rota é excedido.
 * Contém informações para popular os headers de resposta (X-RateLimit-*, Retry-After).
 */
@Getter
public class RateLimitExceededException extends GatewayException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long limit;
    private final long remaining;
    private final Instant resetAt;
    private final long retryAfterSeconds;

    public RateLimitExceededException(long limit, long remaining, Instant resetAt, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                "Rate limit excedido. Tente novamente em " + retryAfterSeconds + " segundos.");
        this.limit = limit;
        this.remaining = remaining;
        this.resetAt = resetAt;
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
