package com.portfolio.api_gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

/**
 * Resposta padronizada de erro para todas as respostas de erro do API Gateway.
 *
 * Garante consistência no formato de erro independente da origem:
 * - Falhas de autenticação/autorização (401/403)
 * - Rate limiting (429)
 * - Serviço indisponível / Circuit Breaker (503)
 * - Erros internos do gateway (500/502/504)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private int status;
    private String error;
    private String errorCode;
    private String message;
    private String path;
    private String requestId;
    private String timestamp;
    private Map<String, Object> details;

    /**
     * Factory method para criar ErrorResponse a partir de HttpStatus e mensagem.
     */
    public static ErrorResponse of(HttpStatus status, String errorCode, String message, String path, String requestId) {
        return ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .errorCode(errorCode)
                .message(message)
                .path(path)
                .requestId(requestId)
                .timestamp(Instant.now().toString())
                .build();
    }
}
