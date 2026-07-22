package com.orvigas.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Security {@link ReactiveJwtDecoder} backed by JJWT.
 *
 * <p>Translates JJWT's verified claims into a Spring Security {@link Jwt}
 * without exposing the signing key or raw token internals.
 *
 * @author orvigas@gmail.com
 */
@Component
@RequiredArgsConstructor
public class JwtDecoder implements ReactiveJwtDecoder {

    private static final String INVALID_TOKEN_MESSAGE = "Invalid JWT";

    private final JwtService jwtService;

    @Override
    public Mono<Jwt> decode(String token) {
        return jwtService.parseSignedToken(token)
                .map(jws -> toJwt(token, jws))
                .onErrorMap(Exception.class, ex -> new BadJwtException(INVALID_TOKEN_MESSAGE, ex));
    }

    private Jwt toJwt(String token, Jws<Claims> jws) {
        Claims claims = jws.getPayload();
        Instant issuedAt = toInstant(claims.getIssuedAt());
        Instant expiresAt = toInstant(claims.getExpiration());
        return new Jwt(token, issuedAt, expiresAt, extractHeaders(jws), claims);
    }

    private Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }

    private Map<String, Object> extractHeaders(Jws<Claims> jws) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("alg", jws.getHeader().getAlgorithm());
        String keyId = jws.getHeader().getKeyId();
        if (keyId != null) {
            headers.put("kid", keyId);
        }
        return headers;
    }
}
