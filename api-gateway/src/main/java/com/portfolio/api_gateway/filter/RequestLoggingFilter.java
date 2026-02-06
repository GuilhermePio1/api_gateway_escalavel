package com.portfolio.api_gateway.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Filtro global de logging de requisições com geração de Request ID.
 *
 * Posição na cadeia de filtros: order=1 (primeiro filtro, antes do rate limiting).
 *
 * Fluxo:
 * 1. Gera ou propaga um Request ID único (header X-Request-Id)
 * 2. Registra log estruturado de entrada da requisição (método, path, client IP)
 * 3. Adiciona o Request ID como header de resposta para rastreabilidade
 * 4. Ao completar, registra log com status HTTP e duração em milissegundos
 * 5. Em caso de erro, registra log de falha com detalhes da exceção
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String START_TIME_ATTR = "requestLoggingStartTime";

    private final Tracer tracer;

    private final XForwardedRemoteAddressResolver ipResolver
            = XForwardedRemoteAddressResolver.maxTrustedIndex(1);

    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();

        // 1. Resolver IDs
        String requestId = resolveRequestId(exchange);
        String traceId = getTraceId();
        String spanId = getSpanId();

        // 2. Mutate Request (Propagar Header para microsserviços downstream)
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(REQUEST_ID_HEADER, requestId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        // 3. Atributos internos e Header de Resposta
        mutatedExchange.getAttributes().put(START_TIME_ATTR, startTime);
        mutatedExchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);

        if (log.isDebugEnabled()) {
            logRequestReceived(mutatedRequest, mutatedExchange, requestId, traceId);
        }

        // 5. Execução do Chain
        return chain.filter(mutatedExchange)
                // Escreve o requestId no Contexto Reativo para propagação (MDC)
                .contextWrite(Context.of(REQUEST_ID_HEADER, requestId))
                .doOnSuccess(v -> logCompletion(mutatedExchange, requestId, traceId, spanId, startTime))
                .doOnError(e -> logError(mutatedExchange, requestId, traceId, spanId, startTime, e));
    }

    @Override
    public int getOrder() {
        // Executa antes do RateLimit (order=2) para garantir que logs capturem requisições bloqueadas (429)
        return 1;
    }

    private String resolveRequestId(ServerWebExchange exchange) {
        String existingId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        return (existingId != null && !existingId.isBlank()) ? existingId : UUID.randomUUID().toString();
    }

    private String getTraceId() {
        Span currentSpan = tracer.currentSpan();
        return (currentSpan != null) ? currentSpan.context().traceId() : "";
    }

    private String getSpanId() {
        Span currentSpan = tracer.currentSpan();
        return (currentSpan != null) ? currentSpan.context().spanId() : "";
    }

    private void logRequestReceived(ServerHttpRequest request, ServerWebExchange exchange, String requestId, String traceId) {
        log.debug("Request received",
                kv("event", "request_received"),
                kv("request_id", requestId),
                kv("trace_id", traceId),
                kv("method", request.getMethod().name()),
                kv("path", request.getPath().value()),
                kv("client_ip", getClientIp(exchange)),
                kv("user_agent", request.getHeaders().getFirst(HttpHeaders.USER_AGENT))
        );
    }

    private void logCompletion(ServerWebExchange exchange, String requestId, String traceId, String spanId, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        ServerHttpResponse response = exchange.getResponse();

        // Null safety para StatusCode (pode ser nulo se a conexão cair)
        int statusCode = Optional.ofNullable(response.getStatusCode())
                .map(HttpStatusCode::value)
                .orElse(0);

        // Verifica se a rota foi encontrada (evita logar 'null' se der 404 antes do route matching)
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = (route != null) ? route.getId() : "unknown_route";

        log.info("Request completed",
                kv("event", "request_completed"),
                kv("request_id", requestId),
                kv("trace_id", traceId),
                kv("span_id", spanId),
                kv("route_id", routeId),
                kv("method", exchange.getRequest().getMethod().name()),
                kv("path", exchange.getRequest().getPath().value()),
                kv("status", statusCode),
                kv("duration_ms", duration),
                kv("client_ip", getClientIp(exchange))
        );
    }

    private void logError(ServerWebExchange exchange, String requestId, String traceId, String spanId, long startTime, Throwable error) {
        long duration = System.currentTimeMillis() - startTime;

        log.error("Request failed",
                kv("event", "request_failed"),
                kv("request_id", requestId),
                kv("trace_id", traceId),
                kv("span_id", spanId),
                kv("method", exchange.getRequest().getMethod().name()),
                kv("path", exchange.getRequest().getPath().value()),
                kv("duration_ms", duration),
                kv("client_ip", getClientIp(exchange)),
                kv("error_type", error.getClass().getSimpleName()),
                kv("error_message", error.getMessage()),
                error
        );
    }

    private String getClientIp(ServerWebExchange exchange) {
        InetSocketAddress address = ipResolver.resolve(exchange);
        return (address != null && address.getAddress() != null) ?
                address.getAddress().getHostAddress()
                : "unknown";
    }
}
