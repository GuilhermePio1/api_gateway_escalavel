package com.portfolio.api_gateway.ratelimit;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Propriedades externalizadas para configuração do rate limiting.
 *
 * Exemplo de configuração em application.yaml:
 *
 * gateway:
 *   rate-limit:
 *     enabled: true
 *     default-policy:
 *       max-requests: 100
 *       window-ms: 60000
 *     route-policies:
 *       orders-service:
 *         max-requests: 10
 *         window-ms: 60000
 */
@Data
@ConfigurationProperties(prefix = "gateway.rate-limit")
public class RateLimitProperties {

    /** Habilita ou desabilita o rate limiting globalmente. */
    private boolean enabled = true;

    /** Política padrão aplicada quando não há política específica para a rota. */
    private PolicyConfig defaultPolicy = new PolicyConfig();

    /** Políticas especificas por route ID. */
    private Map<String, PolicyConfig> routePolicies = new HashMap<>();

    public static class PolicyConfig {
        @Min(1)
        private long maxRequests = 100;

        @Min(1)
        private long windowMs = 60000;

        public RateLimitPolicy toPolicy() {
            return RateLimitPolicy.builder()
                    .maxRequests(maxRequests)
                    .windowMs(windowMs)
                    .build();
        }
    }
}
