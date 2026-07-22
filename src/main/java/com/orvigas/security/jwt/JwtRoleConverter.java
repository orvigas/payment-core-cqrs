package com.orvigas.security.jwt;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Extracts roles from the {@code roles} claim and converts them to Spring Security
 * granted authorities prefixed with {@code ROLE_}.
 *
 * @author orvigas@gmail.com
 */
@Component
public class JwtRoleConverter implements Converter<Jwt, Mono<JwtAuthenticationToken>> {

    private static final String ROLES_CLAIM = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public Mono<JwtAuthenticationToken> convert(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        Collection<? extends GrantedAuthority> authorities = Optional.ofNullable(roles)
                .orElse(Collections.emptyList())
                .stream()
                .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role.toUpperCase()))
                .toList();
        return Mono.just(new JwtAuthenticationToken(jwt, authorities));
    }
}
