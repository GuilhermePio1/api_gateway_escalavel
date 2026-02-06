package com.portfolio.api_gateway.filter.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.api_gateway.dto.ErrorResponse;
import com.portfolio.api_gateway.exception.GatewayException;
import com.portfolio.api_gateway.exception.RateLimitExceededException;
import com.portfolio.api_gateway.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Tratamento centralizado de erros do API Gateway.
 *
 * Intercepta todas as exceções não tratadas e converte em respostas
 * padronizadas ({@link ErrorResponse}) com status HTTP apropriado.
 *
 * Ordem de prioridade: -2 (executa antes do handler de erro padrão do Spring Boot).
 *
 * Exceções tratadas:
 * - {@link GatewayException} e subtipos (RateLimit, ServiceUnavailable, Unauthorized)
 * - {@link AuthenticationException} / {@link AccessDeniedException} (Spring Security)
 * - {@link ResponseStatusException} (Spring WebFlux)
 * - {@link ConnectException} (falha de conexão com downstream)
 * - {@link TimeoutException} (timeout de downstream)
 * - Exceções genéricas (fallback para 500)
 */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GlobalErrorFilter implements ErrorWebExceptionHandler {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        String path = exchange.getRequest().getPath().value();
        String requestId = resolveRequestId(exchange.getRequest());

        ErrorResponse errorResponse;
        HttpStatus status;

        switch (ex) {
            case RateLimitExceededException rle -> {
                status = rle.getStatus();
                errorResponse = buildErrorResponse(rle, path, requestId);
                errorResponse.setDetails(Map.of(
                        "limit", rle.getLimit(),
                        "remaining", rle.getRemaining(),
                        "retryAfterSeconds", rle.getRetryAfterSeconds()
                ));
                response.getHeaders().set("X-RateLimit-Limit", String.valueOf(rle.getLimit()));
                response.getHeaders().set("X-RateLimit-Remaining", String.valueOf(rle.getRemaining()));
                response.getHeaders().set("X-RateLimit-Reset", String.valueOf(rle.getResetAt().toEpochMilli()));
                response.getHeaders().set("Retry-After", String.valueOf(rle.getRetryAfterSeconds()));
                log.warn("Rate limit excedido",
                        kv("event", "rate_limit_exceeded"),
                        kv("path", path),
                        kv("request_id", requestId),
                        kv("limit", rle.getLimit()),
                        kv("retry_after_seconds", rle.getRetryAfterSeconds()));
            }

            case ServiceUnavailableException sue -> {
                status = sue.getStatus();
                errorResponse = buildErrorResponse(sue, path, requestId);
                errorResponse.setDetails(Map.of("service", sue.getServiceName()));
                response.getHeaders().set("X-Fallback-Response", "true");
                log.warn("Servico indisponivel",
                        kv("event", "service_unavailable"),
                        kv("path", path),
                        kv("request_id", requestId),
                        kv("service", sue.getServiceName()));
            }

            case GatewayException ge -> {
                status = ge.getStatus();
                errorResponse = buildErrorResponse(ge, path, requestId);
                log.warn("Gateway exception",
                        kv("event", "gateway_exception"),
                        kv("path", path),
                        kv("request_id", requestId),
                        kv("error_code", ge.getErrorCode()),
                        kv("status", ge.getStatus().value()));
            }

            case InvalidBearerTokenException ignored -> {
                status = HttpStatus.UNAUTHORIZED;
                errorResponse = ErrorResponse.of(status, "INVALID_TOKEN",
                        "Token de acesso invalido ou expirado.", path, requestId);
                log.warn("Token invalido",
                        kv("event", "authentication_failed"),
                        kv("path", path),
                        kv("request_id", requestId),
                        kv("error_type", ex.getClass().getSimpleName()));
            }

            case AuthenticationCredentialsNotFoundException ignored -> {
                status = HttpStatus.UNAUTHORIZED;
                errorResponse = ErrorResponse.of(status, "CREDENTIALS_NOT_FOUND",
                        "Credenciais de autenticacao nao encontradas.", path, requestId);
                log.warn("Credenciais nao encontradas",
                        kv("event", "authentication_failed"),
                        kv("path", path),
                        kv("request_id", requestId));
            }

            case AuthenticationException ignored -> {
                status = HttpStatus.UNAUTHORIZED;
                errorResponse = ErrorResponse.of(status, "UNAUTHORIZED",
                        "Autenticacao necessaria para acessar este recurso.", path, requestId);
                log.warn("Autenticacao falhou",
                        kv("event", "authentication_failed"),
                        kv("path", path),
                        kv("request_id", requestId),
                        kv("error_type", ex.getClass().getSimpleName()));
            }

            case AccessDeniedException ignored -> {
                status = HttpStatus.FORBIDDEN;
                errorResponse = ErrorResponse.of(status, "ACCESS_DENIED",
                        "Permissao insuficiente para acessar este recurso.", path, requestId);
                log.warn("Acesso negado",
                        kv("event", "access_denied"),
                        kv("path", path),
                        kv("request_id", requestId));
            }

            case JwtException ignored -> {
                status = HttpStatus.UNAUTHORIZED;
                errorResponse = ErrorResponse.of(status, "JWT_ERROR",
                        "Erro ao processar token JWT.", path, requestId);
                log.warn("Erro JWT",
                        kv("event", "jwt_error"),
                        kv("path", path),
                        kv("request_id", requestId),
                        kv("error_message", ex.getMessage()));
            }

            case ResponseStatusException rse -> {
                status = HttpStatus.valueOf(rse.getStatusCode().value());
                String errorCode = status.name();
                String message = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();
                errorResponse = ErrorResponse.of(status, errorCode, message, path, requestId);
                log.warn("Response status exception",
                        kv("event", "response_status_exception"),
                        kv("path", path),
                        kv("request_id", requestId),
                        kv("status", status.value()));
            }

            case ConnectException ignored -> {
                status = HttpStatus.BAD_GATEWAY;
                errorResponse = ErrorResponse.of(status, "BAD_GATEWAY",
                        "Nao foi possivel conectar ao servico downstream.", path, requestId);
                log.error("Falha de conexao com downstream",
                        kv("event", "connection_failed"),
                        kv("path", path),
                        kv("request_id", requestId),
                        kv("error_message", ex.getMessage()));
            }

            case TimeoutException ignored -> {
                status = HttpStatus.GATEWAY_TIMEOUT;
                errorResponse = ErrorResponse.of(status, "GATEWAY_TIMEOUT",
                        "O servico downstream nao respondeu dentro do tempo limite.", path, requestId);
                log.error("Timeout de downstream",
                        kv("event", "gateway_timeout"),
                        kv("path", path),
                        kv("request_id", requestId));
            }

            default -> {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                errorResponse = ErrorResponse.of(status, "INTERNAL_ERROR",
                        "Erro interno do gateway. Tente novamente mais tarde.", path, requestId);
                log.error("Erro interno nao tratado",
                        kv("event", "internal_error"),
                        kv("path", path),
                        kv("request_id", requestId),
                        kv("error_type", ex.getClass().getSimpleName()),
                        kv("error_message", ex.getMessage()),
                        ex);
            }
        }

        return writeResponse(response, status, errorResponse);
    }

    private ErrorResponse buildErrorResponse(GatewayException ex, String path, String requestId) {
        return ErrorResponse.of(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), path, requestId);
    }

    private Mono<Void> writeResponse(ServerHttpResponse response, HttpStatus status, ErrorResponse errorResponse) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Erro ao serializar ErrorResponse", e);
            return response.setComplete();
        }
    }

    private String resolveRequestId(ServerHttpRequest request) {
        String requestId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        return (requestId != null && !requestId.isBlank()) ? requestId : UUID.randomUUID().toString();
    }
}
