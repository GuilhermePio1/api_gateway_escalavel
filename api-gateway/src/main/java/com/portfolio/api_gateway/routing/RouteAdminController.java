package com.portfolio.api_gateway.routing;

import com.portfolio.api_gateway.dto.RouteDefinitionDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * API REST para gestão de rotas dinâmicas em runtime.
 * Permite CRUD de rotas sem necessidade de restart do gateway.
 */
@RestController
@RequestMapping("/admin/routes")
@RequiredArgsConstructor
public class RouteAdminController {

    private final DynamicRouteService dynamicRouteService;

    @GetMapping
    public Flux<RouteDefinition> listRoutes() {
        return dynamicRouteService.listRoutes();
    }

    @PostMapping
    public Mono<ResponseEntity<Void>> createRoute(@RequestBody @Valid RouteDefinitionDTO dto) {
        return dynamicRouteService.addRoute(dto)
                .then(Mono.just(
                        ResponseEntity.created(URI.create("/admin/routes/" + dto.getId())).build()
                ));
    }

    @PutMapping("/{routeId}")
    public Mono<ResponseEntity<Void>> updateRoute(
            @PathVariable String routeId,
            @RequestBody @Valid RouteDefinitionDTO dto) {
        dto.setId(routeId);
        return dynamicRouteService.updateRoute(dto)
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    @DeleteMapping("/{routeId}")
    public Mono<ResponseEntity<Void>> deleteRoute(@PathVariable String routeId) {
        return dynamicRouteService.deleteRoute(routeId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }
}
