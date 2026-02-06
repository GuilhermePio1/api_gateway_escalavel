package com.portfolio.api_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.List;

/**
 * Configuração do Redis para o rate limiting.
 * Registry o script Lua como bean para ser injetado no RedisRateLimiter.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisScript<List<Long>> rateLimitScript() {
        DefaultRedisScript<List<Long>> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("scripts/rate_limit.lua")));
        @SuppressWarnings("unchecked")
        Class<List<Long>> resultType = (Class<List<Long>>) (Class<?>) List.class;
        script.setResultType(resultType);
        return script;
    }
}
