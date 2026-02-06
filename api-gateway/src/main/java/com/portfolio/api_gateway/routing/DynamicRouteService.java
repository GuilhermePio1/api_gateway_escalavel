package com.portfolio.api_gateway.routing;

import com.portfolio.api_gateway.dto.RouteDefinitionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serviço para CRUD de rotas dinâmicas em runtime.
 * Permite adicionar, atualizar e remover rotas sem reiniciar o gateway.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicRouteService {

    private final RouteDefinitionWriter routeDefinitionWriter;
    private final RouteDefinitionLocator routeDefinitionLocator;
    private final ApplicationEventPublisher eventPublisher;

    public Flux<RouteDefinition> listRoutes() {
        return routeDefinitionLocator.getRouteDefinitions();
    }

    public Mono<Void> addRoute(RouteDefinitionDTO dto) {
        RouteDefinition route = toRouteDefinition(dto);
        log.info("Adicionando rota dinâmica: id={}, uri={}", route.getId(), route.getUri());

        return routeDefinitionWriter.save(Mono.just(route))
                .doOnSuccess(v -> {
                    eventPublisher.publishEvent(new RefreshRoutesEvent(this));
                    log.info("Rota adicionada com sucesso: {}", route.getId());
                })
                .doOnError(e -> log.error("Erro ao adicionar rota {}: {}", route.getId(), e.getMessage()));
    }

    public Mono<Void> updateRoute(RouteDefinitionDTO dto) {
        RouteDefinition route = toRouteDefinition(dto);
        log.info("Atualizando rota dinamica: id={}", route.getId());

        return routeDefinitionWriter.delete(Mono.just(route.getId()))
                .onErrorResume(e -> Mono.empty())
                .then(routeDefinitionWriter.save(Mono.just(route)))
                .doOnSuccess(v -> {
                    eventPublisher.publishEvent(new RefreshRoutesEvent(this));
                    log.info("Rota atualizada com sucesso: {}", route.getId());
                })
                .doOnError(e -> log.error("Erro ao atualizar rota {}: {}", route.getId(), e.getMessage()));
    }

    public Mono<Void> deleteRoute(String routeId) {
        log.info("Removendo rota dinamica: id={}", routeId);

        return routeDefinitionWriter.delete(Mono.just(routeId))
                .doOnSuccess(v -> {
                    eventPublisher.publishEvent(new RefreshRoutesEvent(this));
                    log.info("Rota removida com sucesso: {}", routeId);
                })
                .doOnError(e -> log.error("Erro ao remover rota {}: {}", routeId, e.getMessage()));
    }

    private RouteDefinition toRouteDefinition(RouteDefinitionDTO dto) {
        RouteDefinition route = new RouteDefinition();
        route.setId(dto.getId());
        route.setUri(URI.create(dto.getUri()));
        route.setOrder(dto.getOrder());
        route.setMetadata(dto.getMetadata() != null ? dto.getMetadata() : Map.of());

        if (dto.getPredicates() != null) {
            List<PredicateDefinition> predicates = dto.getPredicates().stream()
                    .map(p -> {
                        PredicateDefinition predicate = new PredicateDefinition();
                        predicate.setName(p.getName());
                        if (p.getArgs() != null) {
                            predicate.setArgs(new LinkedHashMap<>(p.getArgs()));
                        }
                        return predicate;
                    })
                    .toList();
            route.setPredicates(predicates);
        }

        if (dto.getFilters() != null) {
            List<FilterDefinition> filters = dto.getFilters().stream()
                    .map(f -> {
                        FilterDefinition filter = new FilterDefinition();
                        filter.setName(f.getName());
                        if (f.getArgs() != null) {
                            filter.setArgs(new LinkedHashMap<>(f.getArgs()));
                        }
                        return filter;
                    })
                    .toList();
            route.setFilters(filters);
        }

        return route;
    }
}
