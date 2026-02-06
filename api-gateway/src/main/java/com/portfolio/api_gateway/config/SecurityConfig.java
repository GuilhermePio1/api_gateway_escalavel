package com.portfolio.api_gateway.config;

import com.portfolio.api_gateway.security.jwt.GatewayReactiveJwtAuthenticationConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

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

    public SecurityConfig(GatewayReactiveJwtAuthenticationConverter jwtAuthenticationConverter) {
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
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
