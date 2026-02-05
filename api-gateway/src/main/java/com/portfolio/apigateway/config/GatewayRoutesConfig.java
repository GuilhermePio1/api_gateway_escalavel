package com.portfolio.apigateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator microservicesRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("orders-service-programmatic", route -> route
                        .path("/api/v1/orders/internal/**")
                        .filters(filter -> filter
                                .stripPrefix(4)
                                .circuitBreaker(circuitBreaker -> circuitBreaker
                                        .setName("orders-service-cb")
                                        .setFallbackUri("forward:/fallback/orders")))
                        .uri("lb://orders-service"))
                .route("users-service-programmatic", route -> route
                        .path("/api/v1/users/internal/**")
                        .filters(filter -> filter
                                .stripPrefix(4)
                                .circuitBreaker(circuitBreaker -> circuitBreaker
                                        .setName("users-service-cb")
                                        .setFallbackUri("forward:/fallback/users")))
                        .uri("lb://users-service"))
                .route("products-service-programmatic", route -> route
                        .path("/api/v1/products/internal/**")
                        .filters(filter -> filter
                                .stripPrefix(4)
                                .circuitBreaker(circuitBreaker -> circuitBreaker
                                        .setName("products-service-cb")
                                        .setFallbackUri("forward:/fallback/products")))
                        .uri("lb://products-service"))
                .build();
    }
}
