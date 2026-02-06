package com.portfolio.api_gateway.security.jwt;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Converter reativo que extrai authorities de um token JWT para o Spring Security.
 *
 * Combina três fontes de authorities:
 * 1. Scopes padrão OAuth2 (claim "scope") -> SCOPE_xxx
 * 2. Realm roles do Keycloak (claim "realm_access.roles") -> ROLE_XXX
 * 3. Client roles do Keycloak (claim "resource_access.{client}.roles") -> ROLE_XXX
 */
@Component
public class GatewayReactiveJwtAuthenticationConverter
        implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    private final JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        authorities.addAll(defaultConverter.convert(jwt));

        authorities.addAll(extractRealmRoles(jwt));

        authorities.addAll(extractResourceRoles(jwt));

        return Mono.just(new JwtAuthenticationToken(jwt, authorities, extractPrincipalName(jwt)));
    }

    private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !realmAccess.containsKey("roles")) {
            return Collections.emptySet();
        }

        Object rolesObj = realmAccess.get("roles");
        if (rolesObj instanceof List<?> roles) {
            return roles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    .collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    private Collection<GrantedAuthority> extractResourceRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess == null) {
            return Collections.emptySet();
        }

        Set<GrantedAuthority> authorities = new HashSet<>();

        resourceAccess.values().stream()
                .filter(val -> val instanceof Map)
                .map(val -> (Map<?, ?>) val)
                .filter(clientAccess -> clientAccess.containsKey("roles"))
                .map(clientAccess -> clientAccess.get("roles"))
                .filter(roles -> roles instanceof List)
                .flatMap(roles -> ((List<?>) roles).stream())
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .forEach(authorities::add);

        return authorities;
    }

    private String extractPrincipalName(Jwt jwt) {
        String name = jwt.getClaimAsString("preferred_username");
        return name != null && !name.isBlank() ? name : jwt.getSubject();
    }
}
