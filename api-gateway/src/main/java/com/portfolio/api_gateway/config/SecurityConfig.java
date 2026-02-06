package com.portfolio.api_gateway.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.api_gateway.dto.ErrorResponse;
import com.portfolio.api_gateway.security.jwt.GatewayReactiveJwtAuthenticationConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Configuração de segurança do API Gateway.
 *
 * Implementa OAuth2 Resource Server com JWT para autenticação centralizada.
 * Autorização baseada em scopes (SCOPE_*) por recurso e método HTTP,
 * e RBAC (ROLE_*) para endpoints administrativos.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final GatewayReactiveJwtAuthenticationConverter jwtAuthenticationConverter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(GatewayReactiveJwtAuthenticationConverter jwtAuthenticationConverter,
                          ObjectMapper objectMapper) {
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchanges -> exchanges
                        // --- Endpoints públicos ---
                        .pathMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .pathMatchers("/actuator/prometheus", "/actuator/metrics").permitAll()
                        .pathMatchers("/api/v1/auth/**").permitAll()
                        .pathMatchers("/fallback/**").permitAll()

                        // --- Products: autorização por scope ---
                        .pathMatchers(HttpMethod.GET, "/api/v1/products/**")
                        .hasAuthority("SCOPE_products:read")
                        .pathMatchers(HttpMethod.POST, "/api/v1/products/**")
                        .hasAuthority("SCOPE_products:write")
                        .pathMatchers(HttpMethod.PUT, "/api/v1/products/**")
                        .hasAuthority("SCOPE_products:write")
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/products/**")
                        .hasAuthority("SCOPE_products:write")
                        .pathMatchers(HttpMethod.DELETE, "/api/v1/products/**")
                        .hasAuthority("SCOPE_products:write")

                        // --- Orders: autorização por scope ---
                        .pathMatchers(HttpMethod.GET, "/api/v1/orders/**")
                        .hasAuthority("SCOPE_orders:read")
                        .pathMatchers(HttpMethod.POST, "/api/v1/orders/**")
                        .hasAuthority("SCOPE_orders:write")
                        .pathMatchers(HttpMethod.PUT, "/api/v1/orders/**")
                        .hasAuthority("SCOPE_orders:write")
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/orders/**")
                        .hasAuthority("SCOPE_orders:write")
                        .pathMatchers(HttpMethod.DELETE, "/api/v1/orders/**")
                        .hasAuthority("SCOPE_orders:write")

                        // --- Users: autorização por scope ---
                        .pathMatchers(HttpMethod.GET, "/api/v1/users/**")
                        .hasAuthority("SCOPE_users:read")
                        .pathMatchers(HttpMethod.POST, "/api/v1/users/**")
                        .hasAuthority("SCOPE_users:write")
                        .pathMatchers(HttpMethod.PUT, "/api/v1/users/**")
                        .hasAuthority("SCOPE_users:write")
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/users/**")
                        .hasAuthority("SCOPE_users:write")
                        .pathMatchers(HttpMethod.DELETE, "/api/v1/users/**")
                        .hasAuthority("SCOPE_users:write")

                        // --- Admin: autorização por role ---
                        .pathMatchers("/admin/**").hasAuthority("ROLE_ADMIN")

                        // --- Default: autenticado ---
                        .anyExchange().authenticated()
                )
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                )
                .build();
    }

    /**
     * Decoder JWT customizado com validação de issuer e tolerância de clock skew (60s).
     * Sobrescreve o auto-configurado pelo Spring Boot para controle explícito dos validators.
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {

        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withJwkSetUri(jwkSetUri)
                .build();

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri)
        ));

        return decoder;
    }

    private ServerAuthenticationEntryPoint authenticationEntryPoint() {
        return (exchange, ex) -> {
            String path = exchange.getRequest().getPath().value();
            String requestId = resolveRequestId(exchange.getRequest());

            ErrorResponse errorResponse = ErrorResponse.of(
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "Autenticacao necessaria para acessar este recurso.",
                    path,
                    requestId
            );

            return writeErrorResponse(exchange.getResponse(), HttpStatus.UNAUTHORIZED, errorResponse);
        };
    }

    private ServerAccessDeniedHandler accessDeniedHandler() {
        return (exchange, ex) -> {
            String path = exchange.getRequest().getPath().value();
            String requestId = resolveRequestId(exchange.getRequest());

            ErrorResponse errorResponse = ErrorResponse.of(
                    HttpStatus.FORBIDDEN,
                    "ACCESS_DENIED",
                    "Permissao insuficiente para acessar este recurso.",
                    path,
                    requestId
            );

            return writeErrorResponse(exchange.getResponse(), HttpStatus.FORBIDDEN, errorResponse);
        };
    }

    private Mono<Void> writeErrorResponse(ServerHttpResponse response, HttpStatus status, ErrorResponse errorResponse) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
    }

    private String resolveRequestId(org.springframework.http.server.reactive.ServerHttpRequest request) {
        String requestId = request.getHeaders().getFirst("X-Request-Id");
        return (requestId != null && !requestId.isBlank()) ? requestId : UUID.randomUUID().toString();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of(
                "X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset", "Retry-After"
        ));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}