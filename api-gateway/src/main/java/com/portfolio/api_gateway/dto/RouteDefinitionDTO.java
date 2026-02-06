package com.portfolio.api_gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO para criação e atualização de rotas dinâmicas via API de administração.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteDefinitionDTO {

    @NotBlank(message = "O ID da rota é obrigatório")
    private String id;

    @NotBlank(message = "A URI de destino é obrigatória")
    private String uri;

    @NotEmpty(message = "Pelo menos um predicate é obrigatório")
    private List<PredicateDTO> predicates;

    private List<FilterDTO> filters;

    private Map<String, Object> metadata;

    private int order;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredicateDTO {

        @NotBlank(message = "O nome do predicate é obrigatório")
        private String name;

        private Map<String, String> args;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterDTO {

        @NotBlank(message = "O nome do filter é obrigatório")
        private String name;

        private Map<String, String> args;
    }
}
