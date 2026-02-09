# API Gateway Escalavel

API Gateway de produção construído com **Spring Cloud Gateway**, projetado como ponto de entrada único para um ecossistema de microsserviços. Implementa autenticação OAuth2/JWT, rate limiting distribuído, circuit breakers e observabilidade completa.

## Objetivos Arquiteturais

| Requisito | Meta |
|-----------|------|
| **Latencia** | p99 < 50ms para operações de roteamento |
| **Throughput** | 10.000+ requisições/s por instância |
| **Disponibilidade** | 99.95% uptime |
| **Escalabilidade** | Horizontal, com auto-scaling por CPU e conexões ativas |

## Stack Tecnologica

| Componente | Tecnologia | Versão |
|------------|-----------|--------|
| Runtime | Java | 21 (Virtual Threads + Preview Features) |
| Framework | Spring Boot | 4.0.2 |
| Gateway | Spring Cloud Gateway | 2025.1.0 (Spring Cloud) |
| Seguranca | Spring Security + OAuth2 Resource Server | — |
| Service Discovery | Consul | 1.22 |
| Resiliencia | Resilience4j | 2.3.0 |
| Rate Limiting | Redis (Reactive) | 8.4.0 |
| Metricas | Micrometer + Prometheus | — |
| Tracing | OpenTelemetry (OTLP) | 1.58.0 |
| Logging | Logback + Logstash Encoder | 9.0 |
| Identity Provider | Keycloak | 26.5.2 |

## Arquitetura

```
                          INTERNET
                             |
                     Load Balancer (L7)
                             |
              +--------------+--------------+
              |              |              |
         API Gateway    API Gateway    API Gateway
         Instance 1     Instance 2     Instance N
              |              |              |
              +--------------+--------------+
              |              |              |
           Redis          Consul        Keycloak
        (Rate Limit)   (Discovery)    (OAuth2/OIDC)
              |
   +----------+----------+
   |          |           |
 Orders    Users     Products
 Service   Service    Service
```

### Fluxo de Requisicao

```
Client Request
    |
    v
1. RequestLoggingFilter (order=1)
   - Gera/propaga X-Request-Id
   - Loga metadados da requisicao
    |
    v
2. RateLimitFilter (order=2)
   - Verifica quota no Redis (Sliding Window)
   - Retorna 429 se excedido
    |
    v
3. Security Filter Chain
   - Validacao do token OAuth2/JWT
   - Autorizacao RBAC por scope
    |
    v
4. Route Matching + Service Discovery
   - Matching por path
   - Descoberta de instancias via Consul
   - Load balancing (Round Robin)
    |
    v
5. Resilience Filters
   - Circuit Breaker
   - Retry com exponential backoff
   - Bulkhead (isolamento de recursos)
   - Time Limiter
    |
    v
6. Downstream Microservice
    |
    v
7. GlobalErrorFilter
   - Converte exceções em ErrorResponse padronizado
    |
    v
Client Response
   Headers: X-RateLimit-*, X-Request-Id, X-Gateway-Instance
```

## Estrutura do Projeto

```
api_gateway_escalavel/
├── api-gateway/                          # Aplicacao Spring Boot
│   ├── src/main/java/com/portfolio/api_gateway/
│   │   ├── ApiGatewayEscalavelApplication.java
│   │   ├── config/
│   │   │   ├── GatewayRoutesConfig.java              # Rotas programaticas
│   │   │   ├── SecurityConfig.java                   # OAuth2/JWT + RBAC
│   │   │   ├── RedisConfig.java                      # Conexao Redis + script Lua
│   │   │   ├── ObservabilityConfig.java              # Tracing e metricas
│   │   │   ├── RateLimiterKeyResolverConfig.java     # Estrategia de chave do rate limit
│   │   │   └── JacksonConfig.java                    # Serializacao JSON
│   │   ├── filter/
│   │   │   ├── RequestLoggingFilter.java             # Logging + X-Request-Id
│   │   │   └── error/GlobalErrorFilter.java          # Tratamento centralizado de erros
│   │   ├── security/
│   │   │   └── jwt/GatewayReactiveJwtAuthenticationConverter.java
│   │   ├── ratelimit/
│   │   │   ├── RedisRateLimiter.java                 # Sliding Window com Redis
│   │   │   ├── RateLimitFilter.java                  # Filtro global de rate limit
│   │   │   ├── RateLimiterService.java               # Interface do servico
│   │   │   ├── RateLimitPolicy.java                  # Definicao de politicas
│   │   │   ├── RateLimitProperties.java              # Propriedades configuraveis
│   │   │   └── RateLimitResult.java                  # DTO de resultado
│   │   ├── routing/
│   │   │   ├── RouteAdminController.java             # REST API para CRUD de rotas
│   │   │   └── DynamicRouteService.java              # Gerenciamento de rotas dinamicas
│   │   ├── controller/
│   │   │   └── FallbackController.java               # Fallbacks do circuit breaker
│   │   ├── dto/
│   │   │   ├── ErrorResponse.java                    # Resposta de erro padronizada
│   │   │   └── RouteDefinitionDTO.java               # DTO de criacao de rota
│   │   └── exception/
│   │       ├── GatewayException.java                 # Excecao base
│   │       ├── RateLimitExceededException.java       # 429 Too Many Requests
│   │       ├── ServiceUnavailableException.java      # 503 Service Unavailable
│   │       └── UnauthorizedException.java            # 401 Unauthorized
│   ├── src/main/resources/
│   │   ├── application.yaml                          # Configuracao principal
│   │   └── scripts/rate_limit.lua                    # Script Lua atomico para rate limiting
│   ├── Dockerfile                                    # Build multi-stage
│   └── pom.xml
├── infra/
│   ├── keycloak/realm-export.json                    # Configuracao do realm Keycloak
│   └── prometheus/prometheus.yml                     # Scrape config do Prometheus
├── docker-compose.yml                                # Orquestracao completa
└── arquitetura-api-gateway.md                        # Documento de arquitetura detalhado
```

## Pre-requisitos

- **Java 21+**
- **Maven 3.9+**
- **Docker** e **Docker Compose**

## Como Executar

### 1. Subir a infraestrutura completa (recomendado)

```bash
docker-compose up --build
```

Isso inicia todos os serviços:

| Servico | URL | Descricao |
|---------|-----|-----------|
| **API Gateway** | http://localhost:8080 | Ponto de entrada |
| **Keycloak** | http://localhost:8180 | Painel admin do Identity Provider |
| **Consul** | http://localhost:8500 | UI do Service Discovery |
| **Jaeger** | http://localhost:16686 | UI de Distributed Tracing |
| **Prometheus** | http://localhost:9090 | Metricas e queries |
| **Redis** | localhost:6379 | Rate limiting (sem UI) |

### 2. Build local (sem Docker)

```bash
cd api-gateway
mvn clean package -DskipTests
java --enable-preview -jar target/*.jar
```

> Requer Redis, Consul e Keycloak rodando localmente ou apontando para instâncias externas via variáveis de ambiente.

### Variaveis de Ambiente

| Variavel | Default | Descricao |
|----------|---------|-----------|
| `SPRING_REDIS_HOST` | `localhost` | Host do Redis |
| `SPRING_REDIS_PORT` | `6379` | Porta do Redis |
| `SPRING_CLOUD_CONSUL_HOST` | `localhost` | Host do Consul |
| `SPRING_CLOUD_CONSUL_PORT` | `8500` | Porta do Consul |
| `JWT_ISSUER_URI` | `http://localhost:8180/realms/api-gateway` | Issuer URI do OAuth2 |
| `JWT_JWK_SET_URI` | `http://localhost:8180/realms/api-gateway/protocol/openid-connect/certs` | Endpoint JWKS |
| `MANAGEMENT_OTLP_TRACING_ENDPOINT` | — | Endpoint OTLP para traces |
| `SPRING_CONFIG_ENABLED` | `false` | Habilitar Spring Cloud Config |
| `KEYCLOAK_ADMIN_USER` | `admin` | Usuario admin do Keycloak |
| `KEYCLOAK_ADMIN_PASSWORD` | `admin` | Senha admin do Keycloak |

## Rotas Configuradas

### Microsservicos

| Rota | Servico | Timeout | Retries |
|------|---------|---------|---------|
| `/api/v1/orders/**` | orders-service | 3s | 3 (GET) |
| `/api/v1/users/**` | users-service | 2s | 2 (GET) |
| `/api/v1/products/**` | products-service | 5s | 3 (GET) |

Todas as rotas incluem: Circuit Breaker, Bulkhead, Retry com exponential backoff, e StripPrefix.

### Endpoints Internos

| Endpoint | Acesso | Descricao |
|----------|--------|-----------|
| `GET /actuator/health` | Publico | Health check |
| `GET /actuator/prometheus` | Publico | Metricas Prometheus |
| `GET /actuator/gateway/routes` | Publico | Rotas registradas |
| `POST /admin/routes` | Admin (autenticado) | Criar rota dinamica |
| `DELETE /admin/routes/{id}` | Admin (autenticado) | Remover rota dinamica |
| `GET /fallback/{service}` | Interno | Fallback do circuit breaker |

## Funcionalidades Principais

### Seguranca (OAuth2 + JWT)

- **OAuth2 Resource Server** com validação JWT via JWKS (Keycloak)
- **RBAC baseado em scopes**: autorização por rota e método HTTP
  - `SCOPE_products:read` para `GET /api/v1/products/**`
  - `SCOPE_orders:write` para `POST /api/v1/orders/**`
- **Endpoints publicos**: `/actuator/health/**`, `/actuator/prometheus`
- **CORS** configurado globalmente com headers de rate limit expostos

### Rate Limiting Distribuido

- **Algoritmo**: Sliding Window Log com Redis SortedSets
- **Operações atomicas**: Script Lua garante consistência entre instâncias
- **Estrategias de chave**: User ID (do JWT) com fallback para IP
- **Fail-open**: se Redis estiver indisponível, requisições são permitidas

**Politicas configuradas:**

| Escopo | Max Requisicoes | Janela |
|--------|----------------|--------|
| Default | 100 | 1 min |
| orders-service | 10 | 1 min |
| users-service | 10 | 1 min |
| products-service | 10 | 1 min |

**Headers de resposta:** `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`, `Retry-After`

### Resiliencia (Resilience4j)

**Circuit Breaker:**
- Sliding window: 100 requisições (COUNT_BASED)
- Threshold de falha: 50% (40% para users-service)
- Tempo em OPEN: 60s
- Transição automática OPEN → HALF-OPEN

**Retry:**
- Até 3 tentativas (2 para users-service)
- Exponential backoff: 100ms base, multiplicador 2x
- Apenas para `IOException`

**Bulkhead:**
- orders-service / users-service: 25 chamadas concorrentes
- products-service: 50 chamadas concorrentes

**Time Limiter:**
- orders-service: 3s
- users-service: 2s
- products-service: 5s

### Observabilidade

**Metricas (Prometheus):**
- `gateway.requests.total` - total de requisições processadas
- `gateway.rate_limit.hits` - requisições bloqueadas por rate limit
- `gateway.request.latency` - latência (p50, p95, p99)
- `gateway.connections.active` - conexões ativas
- Histograma de percentis em `http.server.requests`

**Tracing (OpenTelemetry → Jaeger):**
- Propagação W3C Trace Context
- Sampling: 100% (configurável)
- Exportação OTLP para Jaeger

**Logging:**
- Structured logging com correlation ID (traceId)
- Pattern: `timestamp [thread] [traceId] level logger - message`
- Nível DEBUG para `com.portfolio.api_gateway` e `org.springframework.cloud.gateway`

### Gerenciamento Dinamico de Rotas

API REST para criar e remover rotas em runtime sem restart:

```bash
# Criar rota
curl -X POST http://localhost:8080/admin/routes \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "new-service",
    "uri": "lb://new-service",
    "predicates": [{"name": "Path", "args": {"pattern": "/api/v1/new/**"}}],
    "filters": [{"name": "StripPrefix", "args": {"parts": "2"}}]
  }'

# Remover rota
curl -X DELETE http://localhost:8080/admin/routes/new-service \
  -H "Authorization: Bearer <token>"
```

## Docker

### Build Multi-stage

O Dockerfile usa build em dois estágios:

1. **Build**: Maven 3.9 + Eclipse Temurin 21 compila o projeto
2. **Runtime**: Eclipse Temurin 21 JRE Alpine com usuário não-root (`gateway`)

```bash
# Build manual da imagem
cd api-gateway
docker build -t api-gateway .
```

### Infraestrutura (docker-compose)

Todos os serviços estão configurados com:
- **Health checks** para garantir ordem de inicialização
- **Rede isolada** (`gateway-net`)
- **Volumes persistentes** para Redis e Keycloak
- **Dependências declaradas** (api-gateway aguarda Redis, Keycloak e Consul ficarem healthy)

## Arquitetura Detalhada

Para diagramas completos, detalhes de implementação e decisões arquiteturais, consulte o documento [arquitetura-api-gateway.md](arquitetura-api-gateway.md).
