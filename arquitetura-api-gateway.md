# Arquitetura Backend: API Gateway Escalável

## Visão Geral do Sistema

Este documento descreve a arquitetura técnica de um API Gateway escalável, projetado para atuar como ponto de entrada único para um ecossistema de microsserviços. O sistema implementa autenticação centralizada, roteamento dinâmico, rate limiting granular e padrões de resiliência para garantir alta disponibilidade.

### Objetivos Arquiteturais

O gateway foi projetado para atender aos seguintes requisitos não-funcionais:

- **Latência**: p99 < 50ms para operações de roteamento
- **Throughput**: 10.000+ requisições por segundo por instância
- **Disponibilidade**: 99.95% uptime (menos de 4.4 horas de downtime/ano)
- **Escalabilidade**: horizontal, com auto-scaling baseado em métricas de CPU e conexões ativas

---

## Diagrama de Arquitetura

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              INTERNET                                        │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         LOAD BALANCER (L7)                                   │
│                    AWS ALB / Google Cloud LB / Nginx                        │
│                      (SSL Termination, Health Checks)                        │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                    ┌─────────────────┼─────────────────┐
                    ▼                 ▼                 ▼
          ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
          │  API Gateway    │ │  API Gateway    │ │  API Gateway    │
          │   Instance 1    │ │   Instance 2    │ │   Instance N    │
          │                 │ │                 │ │                 │
          │ ┌─────────────┐ │ │ ┌─────────────┐ │ │ ┌─────────────┐ │
          │ │Security     │ │ │ │Security     │ │ │ │Security     │ │
          │ │Filter Chain │ │ │ │Filter Chain │ │ │ │Filter Chain │ │
          │ └─────────────┘ │ │ └─────────────┘ │ │ └─────────────┘ │
          │ ┌─────────────┐ │ │ ┌─────────────┐ │ │ ┌─────────────┐ │
          │ │Rate Limiter │ │ │ │Rate Limiter │ │ │ │Rate Limiter │ │
          │ └─────────────┘ │ │ └─────────────┘ │ │ └─────────────┘ │
          │ ┌─────────────┐ │ │ ┌─────────────┐ │ │ ┌─────────────┐ │
          │ │Circuit      │ │ │ │Circuit      │ │ │ │Circuit      │ │
          │ │Breaker      │ │ │ │Breaker      │ │ │ │Breaker      │ │
          │ └─────────────┘ │ │ └─────────────┘ │ │ └─────────────┘ │
          └────────┬────────┘ └────────┬────────┘ └────────┬────────┘
                   │                   │                   │
                   └───────────────────┼───────────────────┘
                                       │
    ┌──────────────────────────────────┼──────────────────────────────────┐
    │                                  │                                   │
    ▼                                  ▼                                   ▼
┌────────────┐                  ┌────────────┐                     ┌────────────┐
│   Redis    │                  │  Service   │                     │ Config     │
│  Cluster   │                  │ Discovery  │                     │ Server     │
│            │                  │            │                     │            │
│ • Sessions │                  │  Consul /  │                     │  Spring    │
│ • Rate     │                  │  Eureka    │                     │  Cloud     │
│   Limits   │                  │            │                     │  Config    │
│ • Cache    │                  │            │                     │            │
└────────────┘                  └──────┬─────┘                     └────────────┘
                                       │
        ┌──────────────────────────────┼──────────────────────────────┐
        │                              │                              │
        ▼                              ▼                              ▼
┌───────────────┐            ┌───────────────┐            ┌───────────────┐
│ Microservice  │            │ Microservice  │            │ Microservice  │
│    Orders     │            │    Users      │            │   Products    │
│               │            │               │            │               │
│  Port: 8081   │            │  Port: 8082   │            │  Port: 8083   │
└───────────────┘            └───────────────┘            └───────────────┘

                    OBSERVABILITY STACK
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                  │
│  │ Prometheus   │───▶│   Grafana    │    │    Jaeger    │                  │
│  │              │    │              │    │              │                  │
│  │  (Metrics)   │    │ (Dashboards) │    │  (Tracing)   │                  │
│  └──────────────┘    └──────────────┘    └──────────────┘                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Stack Tecnológica

| Componente | Tecnologia | Versão | Justificativa |
|------------|------------|--------|---------------|
| Runtime | Java | 21+    | Virtual Threads para concorrência massiva sem overhead de threads OS |
| Framework | Spring Boot | 4.0+   | Ecossistema maduro com suporte nativo a GraalVM e Virtual Threads |
| Gateway | Spring Cloud Gateway | 5.0+   | Programação reativa com Project Reactor, extensível via filtros |
| Segurança | Spring Security + OAuth2 | 7.0+   | Suporte completo a OIDC, JWT e Resource Server |
| Service Discovery | Consul | 1.22+  | Service mesh integrado, health checks avançados, KV store |
| Resiliência | Resilience4j | 2.3+   | Lightweight, funcional, integração nativa com Spring Boot |
| Cache/Rate Limit | Redis | 8.6+   | Estruturas de dados atômicas, Lua scripting para rate limiting distribuído |
| Configuração | Spring Cloud Config | 5.0+   | Configuração centralizada com refresh sem restart |
| Métricas | Micrometer + Prometheus | 1.16+  | Instrumentação dimensional, padrão de mercado |
| Tracing | OpenTelemetry | 1.53+  | Vendor-neutral, auto-instrumentação, contexto propagado |
| Logs | Logback + Loki | 2.0+   | Structured logging em JSON, agregação centralizada |

---

## Estrutura de Pacotes

```
com.portfolio.gateway
├── GatewayApplication.java
│
├── config/
│   ├── GatewayConfig.java              # Configuração de rotas e predicates
│   ├── SecurityConfig.java             # OAuth2, JWT, CORS
│   ├── ResilienceConfig.java           # Circuit breakers, rate limiters
│   ├── RedisConfig.java                # Conexões e serialização
│   └── ObservabilityConfig.java        # Métricas, tracing, logging
│
├── filter/
│   ├── pre/
│   │   ├── AuthenticationFilter.java   # Validação de tokens JWT
│   │   ├── RateLimitFilter.java        # Controle de taxa por usuário/IP
│   │   ├── RequestValidationFilter.java # Sanitização e validação
│   │   └── RequestLoggingFilter.java   # Logging de entrada
│   │
│   ├── post/
│   │   ├── ResponseLoggingFilter.java  # Logging de saída
│   │   └── HeaderEnrichmentFilter.java # Headers de segurança
│   │
│   └── error/
│       └── GlobalErrorFilter.java      # Tratamento centralizado de erros
│
├── security/
│   ├── jwt/
│   │   ├── JwtTokenProvider.java       # Parsing e validação de JWT
│   │   └── JwtAuthenticationManager.java
│   │
│   ├── oauth2/
│   │   ├── OAuth2ResourceServerConfig.java
│   │   └── CustomJwtDecoder.java       # Decoder com cache de JWKS
│   │
│   └── rbac/
│       ├── PermissionEvaluator.java    # Avaliação de permissões
│       └── RouteAuthorizationManager.java
│
├── ratelimit/
│   ├── RateLimiterService.java         # Interface do serviço
│   ├── RedisRateLimiter.java           # Implementação com sliding window
│   ├── RateLimitPolicy.java            # Políticas configuráveis
│   └── RateLimitKeyResolver.java       # Extração de chave (user/IP/API key)
│
├── resilience/
│   ├── CircuitBreakerRegistry.java     # Registro de circuit breakers
│   ├── RetryRegistry.java              # Políticas de retry
│   └── FallbackHandler.java            # Respostas de fallback
│
├── discovery/
│   ├── ServiceRegistry.java            # Abstração de service discovery
│   ├── ConsulServiceRegistry.java      # Implementação Consul
│   └── LoadBalancerConfig.java         # Estratégias de balanceamento
│
├── routing/
│   ├── DynamicRouteService.java        # CRUD de rotas em runtime
│   ├── RouteDefinitionRepository.java  # Persistência de rotas
│   └── RouteRefreshListener.java       # Listener de atualizações
│
├── observability/
│   ├── MetricsService.java             # Métricas customizadas
│   ├── TracingFilter.java              # Propagação de trace context
│   └── HealthIndicators.java           # Health checks customizados
│
├── dto/
│   ├── ErrorResponse.java              # Resposta padronizada de erro
│   ├── RateLimitInfo.java              # Info de rate limit nos headers
│   └── RouteDefinitionDTO.java         # DTO para API de rotas
│
└── exception/
    ├── GatewayException.java           # Exceção base
    ├── RateLimitExceededException.java
    ├── UnauthorizedException.java
    └── ServiceUnavailableException.java
```

---

## Fluxo de Requisição

O diagrama abaixo ilustra o ciclo de vida completo de uma requisição através do gateway:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                          REQUEST LIFECYCLE                                    │
└──────────────────────────────────────────────────────────────────────────────┘

    Client Request
          │
          ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. SECURITY FILTER CHAIN                                                     │
│    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐                    │
│    │   CORS      │───▶│   OAuth2    │───▶│    RBAC     │                    │
│    │   Check     │    │   Resource  │    │   Check     │                    │
│    │             │    │   Server    │    │             │                    │
│    └─────────────┘    └─────────────┘    └─────────────┘                    │
│          │                  │                  │                             │
│          ▼                  ▼                  ▼                             │
│    [Origin OK?]      [Token Valid?]     [Has Permission?]                   │
│     No → 403          No → 401           No → 403                           │
└─────────────────────────────────────────────────────────────────────────────┘
          │ Yes
          ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 2. PRE-FILTERS (Ordered by Priority)                                         │
│                                                                              │
│    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐                    │
│    │  Request    │───▶│    Rate     │───▶│  Request    │                    │
│    │  Logging    │    │   Limiter   │    │ Validation  │                    │
│    │  (order=1)  │    │  (order=2)  │    │  (order=3)  │                    │
│    └─────────────┘    └─────────────┘    └─────────────┘                    │
│          │                  │                  │                             │
│          ▼                  ▼                  ▼                             │
│    [Log Request]     [Under Limit?]    [Valid Schema?]                      │
│                       No → 429          No → 400                            │
└─────────────────────────────────────────────────────────────────────────────┘
          │ Yes
          ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 3. ROUTING                                                                   │
│                                                                              │
│    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐                    │
│    │   Route     │───▶│   Service   │───▶│    Load     │                    │
│    │  Matching   │    │  Discovery  │    │  Balancer   │                    │
│    │             │    │   (Consul)  │    │             │                    │
│    └─────────────┘    └─────────────┘    └─────────────┘                    │
│          │                  │                  │                             │
│          ▼                  ▼                  ▼                             │
│    [Match Route]     [Get Instances]   [Select Target]                      │
│     No → 404         No → 503                                               │
└─────────────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 4. RESILIENCE                                                                │
│                                                                              │
│    ┌─────────────────────────────────────────────────────────┐              │
│    │                   Circuit Breaker                        │              │
│    │                                                          │              │
│    │   CLOSED ──────────▶ OPEN ──────────▶ HALF-OPEN         │              │
│    │     │                  │                  │              │              │
│    │   [Pass]          [Fallback]          [Test]            │              │
│    │     │                  │                  │              │              │
│    └─────│──────────────────│──────────────────│─────────────┘              │
│          │                  │                  │                             │
│          ▼                  ▼                  ▼                             │
│    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐                    │
│    │   Retry     │    │  Fallback   │    │  Limited    │                    │
│    │   Policy    │    │  Response   │    │   Traffic   │                    │
│    │             │    │             │    │             │                    │
│    └─────────────┘    └─────────────┘    └─────────────┘                    │
└─────────────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 5. DOWNSTREAM CALL                                                           │
│                                                                              │
│    ┌─────────────┐         ┌─────────────────────────┐                      │
│    │   HTTP      │────────▶│     Microservice        │                      │
│    │   Client    │◀────────│                         │                      │
│    │  (WebClient)│         │  • Orders Service       │                      │
│    └─────────────┘         │  • Users Service        │                      │
│          │                 │  • Products Service     │                      │
│          │                 └─────────────────────────┘                      │
│          ▼                                                                   │
│    [Response or Timeout (30s default)]                                      │
└─────────────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 6. POST-FILTERS                                                              │
│                                                                              │
│    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐                    │
│    │  Response   │───▶│   Header    │───▶│  Metrics    │                    │
│    │  Logging    │    │ Enrichment  │    │  Recording  │                    │
│    │             │    │             │    │             │                    │
│    └─────────────┘    └─────────────┘    └─────────────┘                    │
│          │                  │                  │                             │
│          ▼                  ▼                  ▼                             │
│    [Log Response]   [Add Security    [Record Latency,                       │
│                      Headers]         Status, etc.]                         │
└─────────────────────────────────────────────────────────────────────────────┘
          │
          ▼
    Client Response
```

---

## Componentes Principais

### Segurança (OAuth2/OIDC + JWT)

O gateway implementa o padrão Resource Server do OAuth2, validando tokens JWT emitidos por um Identity Provider externo (Keycloak, Auth0, Okta).

**Fluxo de Autenticação:**

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────────┐
│  Client  │     │ Identity │     │   API    │     │ Microservice │
│          │     │ Provider │     │ Gateway  │     │              │
└────┬─────┘     └────┬─────┘     └────┬─────┘     └──────┬───────┘
     │                │                │                   │
     │ 1. Login       │                │                   │
     │───────────────▶│                │                   │
     │                │                │                   │
     │ 2. JWT Token   │                │                   │
     │◀───────────────│                │                   │
     │                │                │                   │
     │ 3. Request + Bearer Token       │                   │
     │────────────────────────────────▶│                   │
     │                │                │                   │
     │                │ 4. Validate    │                   │
     │                │    JWT (JWKS)  │                   │
     │                │◀──────────────▶│                   │
     │                │                │                   │
     │                │                │ 5. Forward Request│
     │                │                │──────────────────▶│
     │                │                │                   │
     │                │                │ 6. Response       │
     │                │                │◀──────────────────│
     │                │                │                   │
     │ 7. Response                     │                   │
     │◀────────────────────────────────│                   │
```

**Configuração de Segurança:**

```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeExchange(exchanges -> exchanges
                // Endpoints públicos
                .pathMatchers("/actuator/health/**").permitAll()
                .pathMatchers("/api/v1/auth/**").permitAll()
                
                // Endpoints protegidos por scope
                .pathMatchers(HttpMethod.GET, "/api/v1/products/**")
                    .hasAuthority("SCOPE_products:read")
                .pathMatchers(HttpMethod.POST, "/api/v1/orders/**")
                    .hasAuthority("SCOPE_orders:write")
                
                // Default: autenticado
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )
            .build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
            .withJwkSetUri("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
            .build();
        
        // Cache de JWKS por 5 minutos
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer("${jwt.issuer}"),
            new JwtTimestampValidator(Duration.ofSeconds(60))
        ));
        
        return decoder;
    }
}
```

---

### Rate Limiting Distribuído

O rate limiting é implementado usando Redis com o algoritmo **Sliding Window Log**, garantindo precisão e consistência entre múltiplas instâncias do gateway.

**Arquitetura do Rate Limiter:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         RATE LIMITING ARCHITECTURE                           │
└─────────────────────────────────────────────────────────────────────────────┘

    Request
       │
       ▼
┌──────────────────┐
│  Key Resolver    │──────▶ Estratégias de Resolução:
│                  │        • Por User ID (JWT claim)
│  Extrai chave    │        • Por API Key (header)
│  identificadora  │        • Por IP (fallback)
└────────┬─────────┘        • Por Rota + User
         │
         ▼
┌──────────────────┐     ┌────────────────────────────────────┐
│  Policy Matcher  │────▶│         Rate Limit Policies        │
│                  │     │                                    │
│  Seleciona       │     │  Tier        │ Requests │ Window  │
│  política        │     │──────────────│──────────│─────────│
│  aplicável       │     │  Free        │   100    │   1h    │
└────────┬─────────┘     │  Standard    │  1000    │   1h    │
         │               │  Premium     │ 10000    │   1h    │
         │               │  Enterprise  │ Unlimited│   -     │
         ▼               └────────────────────────────────────┘
┌──────────────────┐
│  Redis Cluster   │
│                  │
│  Sliding Window  │◀────── Lua Script (Atômico):
│  Algorithm       │        1. Remove entradas expiradas
│                  │        2. Conta requisições na janela
│  Key: user:123   │        3. Adiciona nova entrada
│  Value: SortedSet│        4. Retorna count e TTL
│  Score: timestamp│
└────────┬─────────┘
         │
         ▼
┌──────────────────┐     ┌────────────────────────────────────┐
│  Decision        │     │       Response Headers             │
│                  │     │                                    │
│  Allow or Deny   │────▶│  X-RateLimit-Limit: 1000          │
│                  │     │  X-RateLimit-Remaining: 847        │
│                  │     │  X-RateLimit-Reset: 1706789200     │
└──────────────────┘     │  Retry-After: 3600 (se bloqueado) │
                         └────────────────────────────────────┘
```

**Implementação do Rate Limiter:**

```java
@Service
@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiterService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final RedisScript<List<Long>> rateLimitScript;
    
    private static final String KEY_PREFIX = "rate_limit:";

    @Override
    public Mono<RateLimitResult> isAllowed(String key, RateLimitPolicy policy) {
        String redisKey = KEY_PREFIX + key;
        long now = Instant.now().toEpochMilli();
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
        });
    }
}
```

**Script Lua para Sliding Window:**

```lua
-- rate_limit.lua
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window_start = tonumber(ARGV[2])
local max_requests = tonumber(ARGV[3])
local window_ms = tonumber(ARGV[4])

-- Remove entradas fora da janela
redis.call('ZREMRANGEBYSCORE', key, 0, window_start)

-- Conta requisições na janela atual
local current_count = redis.call('ZCARD', key)

-- Se dentro do limite, adiciona nova requisição
if current_count < max_requests then
    redis.call('ZADD', key, now, now .. ':' .. math.random())
    current_count = current_count + 1
end

-- Define TTL para limpeza automática
redis.call('PEXPIRE', key, window_ms)

-- Retorna contagem atual e TTL
local ttl = redis.call('PTTL', key)
return {current_count, ttl}
```

---

### Resiliência com Resilience4j

A camada de resiliência protege o gateway e os microsserviços downstream contra falhas em cascata.

**Padrões Implementados:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         RESILIENCE PATTERNS                                  │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│ CIRCUIT BREAKER                                                              │
│                                                                              │
│    Estados:                                                                  │
│    ┌────────────────────────────────────────────────────────────┐           │
│    │                                                            │           │
│    │   ┌──────────┐    failure rate    ┌──────────┐            │           │
│    │   │  CLOSED  │────────────────────▶│   OPEN   │            │           │
│    │   │          │     >= 50%         │          │            │           │
│    │   │ (Normal) │                    │(Fallback)│            │           │
│    │   └────┬─────┘                    └────┬─────┘            │           │
│    │        │                               │                   │           │
│    │        │ success                       │ wait 60s          │           │
│    │        │                               │                   │           │
│    │        │         ┌──────────┐          │                   │           │
│    │        └─────────│HALF-OPEN │◀─────────┘                   │           │
│    │                  │          │                              │           │
│    │                  │ (Testing)│                              │           │
│    │                  └────┬─────┘                              │           │
│    │                       │                                    │           │
│    │          success: CLOSED / failure: OPEN                   │           │
│    └────────────────────────────────────────────────────────────┘           │
│                                                                              │
│    Configuração:                                                             │
│    • slidingWindowSize: 100 requisições                                     │
│    • failureRateThreshold: 50%                                              │
│    • waitDurationInOpenState: 60s                                           │
│    • permittedNumberOfCallsInHalfOpenState: 10                              │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│ RETRY                                                                        │
│                                                                              │
│    Tentativa 1 ──▶ Falha ──▶ Wait 100ms ──▶ Tentativa 2 ──▶ Falha          │
│                                                    │                         │
│                                                    ▼                         │
│                                           Wait 200ms (exponential)          │
│                                                    │                         │
│                                                    ▼                         │
│                                             Tentativa 3 ──▶ Sucesso         │
│                                                                              │
│    Configuração:                                                             │
│    • maxAttempts: 3                                                         │
│    • waitDuration: 100ms (base)                                             │
│    • exponentialBackoffMultiplier: 2                                        │
│    • retryExceptions: [IOException, TimeoutException]                       │
│    • ignoreExceptions: [BusinessException]                                  │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│ BULKHEAD                                                                     │
│                                                                              │
│    Isolamento de recursos por serviço:                                      │
│                                                                              │
│    ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                       │
│    │   Orders    │  │   Users     │  │  Products   │                       │
│    │   Service   │  │   Service   │  │   Service   │                       │
│    │             │  │             │  │             │                       │
│    │ max: 25     │  │ max: 25     │  │ max: 50     │                       │
│    │ concurrent  │  │ concurrent  │  │ concurrent  │                       │
│    └─────────────┘  └─────────────┘  └─────────────┘                       │
│                                                                              │
│    Benefício: Falha no Orders não afeta Products                            │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│ TIME LIMITER                                                                 │
│                                                                              │
│    Request ──▶ [Timeout: 3s] ──▶ TimeoutException                           │
│                     │                                                        │
│                     ▼                                                        │
│              Circuit Breaker conta como falha                               │
│                                                                              │
│    Configuração por serviço:                                                │
│    • orders-service: 3s                                                     │
│    • users-service: 2s                                                      │
│    • products-service: 5s (catálogo maior)                                  │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Configuração YAML:**

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 100
        minimumNumberOfCalls: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 60s
        permittedNumberOfCallsInHalfOpenState: 10
        automaticTransitionFromOpenToHalfOpenEnabled: true
        recordExceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
          - org.springframework.web.reactive.function.client.WebClientResponseException
    instances:
      orders-service:
        baseConfig: default
      users-service:
        baseConfig: default
        failureRateThreshold: 40
      products-service:
        baseConfig: default
        slidingWindowSize: 200

  retry:
    configs:
      default:
        maxAttempts: 3
        waitDuration: 100ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.io.IOException
    instances:
      orders-service:
        baseConfig: default
      users-service:
        baseConfig: default
        maxAttempts: 2

  timelimiter:
    configs:
      default:
        timeoutDuration: 3s
        cancelRunningFuture: true
    instances:
      products-service:
        timeoutDuration: 5s

  bulkhead:
    configs:
      default:
        maxConcurrentCalls: 25
        maxWaitDuration: 0
    instances:
      products-service:
        maxConcurrentCalls: 50
```

---

### Service Discovery com Consul

O gateway integra-se ao Consul para descoberta dinâmica de serviços e balanceamento de carga.

**Arquitetura de Service Discovery:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        SERVICE DISCOVERY FLOW                                │
└─────────────────────────────────────────────────────────────────────────────┘

┌──────────────────┐          ┌──────────────────┐
│   API Gateway    │          │   Consul Agent   │
│                  │          │                  │
│  ┌────────────┐  │  Watch   │  ┌────────────┐  │
│  │  Service   │◀─┼──────────┼──│  Service   │  │
│  │  Cache     │  │  Changes │  │  Catalog   │  │
│  └────────────┘  │          │  └────────────┘  │
└────────┬─────────┘          └────────┬─────────┘
         │                             │
         │ Query healthy instances     │ Health Checks (10s)
         │                             │
         ▼                             ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                          SERVICE INSTANCES                                │
│                                                                          │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐          │
│  │ orders-service  │  │ orders-service  │  │ orders-service  │          │
│  │ instance-1      │  │ instance-2      │  │ instance-3      │          │
│  │ 10.0.1.10:8081  │  │ 10.0.1.11:8081  │  │ 10.0.1.12:8081  │          │
│  │ [healthy ✓]     │  │ [healthy ✓]     │  │ [unhealthy ✗]   │          │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘          │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────┐
│  Load Balancer   │
│                  │
│  Round Robin /   │
│  Weighted /      │
│  Least Conn      │
└──────────────────┘
```

**Configuração de Rotas Dinâmicas:**

```yaml
spring:
  cloud:
    consul:
      host: localhost
      port: 8500
      discovery:
        enabled: true
        prefer-ip-address: true
        health-check-interval: 10s
        health-check-path: /actuator/health
        
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
      
      routes:
        - id: orders-service
          uri: lb://orders-service
          predicates:
            - Path=/api/v1/orders/**
          filters:
            - name: CircuitBreaker
              args:
                name: orders-service
                fallbackUri: forward:/fallback/orders
            - name: Retry
              args:
                retries: 3
                statuses: BAD_GATEWAY,SERVICE_UNAVAILABLE
            - StripPrefix=2
            
        - id: users-service
          uri: lb://users-service
          predicates:
            - Path=/api/v1/users/**
          filters:
            - name: CircuitBreaker
              args:
                name: users-service
                fallbackUri: forward:/fallback/users
            - StripPrefix=2
            
        - id: products-service
          uri: lb://products-service
          predicates:
            - Path=/api/v1/products/**
          filters:
            - name: CircuitBreaker
              args:
                name: products-service
                fallbackUri: forward:/fallback/products
            - StripPrefix=2
```

---

### Observabilidade

O gateway implementa os três pilares da observabilidade: métricas, logs e traces distribuídos.

**Stack de Observabilidade:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        OBSERVABILITY ARCHITECTURE                            │
└─────────────────────────────────────────────────────────────────────────────┘

                         API Gateway
                              │
         ┌────────────────────┼────────────────────┐
         │                    │                    │
         ▼                    ▼                    ▼
    ┌─────────┐         ┌─────────┐         ┌─────────┐
    │ METRICS │         │  LOGS   │         │ TRACES  │
    │         │         │         │         │         │
    │Micrometer│        │ Logback │         │OpenTelem│
    │         │         │  +JSON  │         │  etry   │
    └────┬────┘         └────┬────┘         └────┬────┘
         │                   │                   │
         ▼                   ▼                   ▼
    ┌─────────┐         ┌─────────┐         ┌─────────┐
    │Prometheus│        │  Loki   │         │  Jaeger │
    │         │         │         │         │         │
    │ :9090   │         │ :3100   │         │ :16686  │
    └────┬────┘         └────┬────┘         └────┬────┘
         │                   │                   │
         └───────────────────┴───────────────────┘
                             │
                             ▼
                      ┌─────────────┐
                      │   Grafana   │
                      │             │
                      │ Dashboards  │
                      │  Alerting   │
                      │             │
                      │   :3000     │
                      └─────────────┘
```

**Métricas Customizadas:**

```java
@Component
@RequiredArgsConstructor
public class GatewayMetrics {

    private final MeterRegistry registry;
    
    // Contadores
    private Counter requestsTotal;
    private Counter rateLimitHits;
    private Counter circuitBreakerOpenCount;
    
    // Gauges
    private AtomicInteger activeConnections;
    
    // Histogramas
    private Timer requestLatency;
    private DistributionSummary requestSize;

    @PostConstruct
    public void init() {
        requestsTotal = Counter.builder("gateway.requests.total")
            .description("Total de requisições processadas")
            .tag("application", "api-gateway")
            .register(registry);
            
        rateLimitHits = Counter.builder("gateway.rate_limit.hits")
            .description("Requisições bloqueadas por rate limit")
            .register(registry);
            
        requestLatency = Timer.builder("gateway.request.latency")
            .description("Latência das requisições")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);
            
        activeConnections = registry.gauge(
            "gateway.connections.active",
            new AtomicInteger(0)
        );
    }
    
    public void recordRequest(String service, String method, int status, long durationMs) {
        requestsTotal.increment();
        requestLatency.record(Duration.ofMillis(durationMs));
        
        registry.counter("gateway.requests",
            "service", service,
            "method", method,
            "status", String.valueOf(status / 100) + "xx"
        ).increment();
    }
}
```

**Structured Logging:**

```java
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String traceId = exchange.getRequest().getHeaders()
            .getFirst("X-Trace-Id");
        
        return chain.filter(exchange)
            .doOnSuccess(v -> {
                long duration = System.currentTimeMillis() - startTime;
                
                log.info("Request completed",
                    kv("trace_id", traceId),
                    kv("method", exchange.getRequest().getMethod()),
                    kv("path", exchange.getRequest().getPath().value()),
                    kv("status", exchange.getResponse().getStatusCode()),
                    kv("duration_ms", duration),
                    kv("client_ip", getClientIp(exchange)),
                    kv("user_agent", exchange.getRequest().getHeaders()
                        .getFirst("User-Agent"))
                );
            })
            .doOnError(e -> {
                log.error("Request failed",
                    kv("trace_id", traceId),
                    kv("error", e.getMessage()),
                    e
                );
            });
    }
}
```

---

## Configuração Dinâmica

O gateway suporta atualização de rotas e políticas em tempo de execução via Spring Cloud Config.

**Arquitetura de Configuração:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     DYNAMIC CONFIGURATION                                    │
└─────────────────────────────────────────────────────────────────────────────┘

┌───────────────┐      ┌───────────────┐      ┌───────────────┐
│    Git Repo   │─────▶│ Config Server │─────▶│  API Gateway  │
│               │ pull │               │ push │               │
│ routes.yml    │      │  /refresh     │      │ @RefreshScope │
│ security.yml  │      │               │      │               │
│ ratelimit.yml │      │  /bus-refresh │      │               │
└───────────────┘      └───────────────┘      └───────────────┘
                              │
                              │ Spring Cloud Bus (RabbitMQ/Kafka)
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
       ┌───────────┐   ┌───────────┐   ┌───────────┐
       │ Gateway 1 │   │ Gateway 2 │   │ Gateway N │
       │           │   │           │   │           │
       │ Refresh!  │   │ Refresh!  │   │ Refresh!  │
       └───────────┘   └───────────┘   └───────────┘
```

**API de Gestão de Rotas:**

```java
@RestController
@RequestMapping("/admin/routes")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class RouteAdminController {

    private final RouteDefinitionWriter routeWriter;
    private final ApplicationEventPublisher publisher;

    @PostMapping
    public Mono<ResponseEntity<Void>> createRoute(
            @RequestBody @Valid RouteDefinitionDTO dto) {
        
        RouteDefinition route = toRouteDefinition(dto);
        
        return routeWriter.save(Mono.just(route))
            .then(Mono.fromRunnable(() -> 
                publisher.publishEvent(new RefreshRoutesEvent(this))))
            .then(Mono.just(ResponseEntity.created(
                URI.create("/admin/routes/" + route.getId())).build()));
    }

    @DeleteMapping("/{routeId}")
    public Mono<ResponseEntity<Void>> deleteRoute(@PathVariable String routeId) {
        return routeWriter.delete(Mono.just(routeId))
            .then(Mono.fromRunnable(() -> 
                publisher.publishEvent(new RefreshRoutesEvent(this))))
            .then(Mono.just(ResponseEntity.noContent().build()));
    }
}
```

---

## Deployment

### Docker Compose (Desenvolvimento)

```yaml
version: '3.8'

services:
  api-gateway:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - SPRING_CLOUD_CONSUL_HOST=consul
      - SPRING_REDIS_HOST=redis
      - OTEL_EXPORTER_JAEGER_ENDPOINT=http://jaeger:14250
    depends_on:
      - consul
      - redis
      - config-server
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  consul:
    image: consul:1.18
    ports:
      - "8500:8500"
    command: agent -dev -ui -client=0.0.0.0

  redis:
    image: redis:7.2-alpine
    ports:
      - "6379:6379"
    command: redis-server --appendonly yes

  config-server:
    image: config-server:latest
    ports:
      - "8888:8888"
    environment:
      - SPRING_CLOUD_CONFIG_SERVER_GIT_URI=https://github.com/org/config-repo

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin

  jaeger:
    image: jaegertracing/all-in-one:latest
    ports:
      - "16686:16686"
      - "14250:14250"
```

### Kubernetes (Produção)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
  labels:
    app: api-gateway
spec:
  replicas: 3
  selector:
    matchLabels:
      app: api-gateway
  template:
    metadata:
      labels:
        app: api-gateway
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      containers:
        - name: api-gateway
          image: registry/api-gateway:latest
          ports:
            - containerPort: 8080
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "kubernetes"
            - name: JAVA_OPTS
              value: "-XX:+UseZGC -XX:MaxRAMPercentage=75.0"
          resources:
            requests:
              memory: "512Mi"
              cpu: "500m"
            limits:
              memory: "1Gi"
              cpu: "1000m"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 5
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: api-gateway-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: api-gateway
  minReplicas: 3
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Pods
      pods:
        metric:
          name: http_requests_per_second
        target:
          type: AverageValue
          averageValue: "1000"
```

---

## Considerações de Segurança

| Aspecto | Implementação |
|---------|---------------|
| **Autenticação** | OAuth2/OIDC com JWT, validação de assinatura via JWKS |
| **Autorização** | RBAC baseado em scopes do token, políticas por rota |
| **Rate Limiting** | Sliding window distribuído, proteção contra DDoS |
| **Input Validation** | Sanitização de headers, validação de payload |
| **TLS** | Terminação no Load Balancer, mTLS interno opcional |
| **Headers de Segurança** | HSTS, X-Content-Type-Options, X-Frame-Options |
| **Audit Logging** | Registro de todas as requisições com correlation ID |
| **Secret Management** | HashiCorp Vault para credenciais sensíveis |

---

## Roadmap de Evolução

1. **Fase 1 (MVP)**: Gateway básico com roteamento, autenticação JWT e rate limiting
2. **Fase 2**: Circuit breakers, retry policies e observabilidade completa
3. **Fase 3**: Configuração dinâmica, API de gestão de rotas
4. **Fase 4**: Service mesh integration (Istio/Linkerd), gRPC support
5. **Fase 5**: Edge caching, GraphQL federation gateway

---

## Referências

- Spring Cloud Gateway Documentation
- Resilience4j User Guide
- OAuth 2.0 RFC 6749
- OpenTelemetry Specification
- Consul Service Discovery
- Redis Rate Limiting Patterns
