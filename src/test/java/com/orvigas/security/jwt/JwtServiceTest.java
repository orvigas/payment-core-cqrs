package com.orvigas.security.jwt;

import com.orvigas.security.config.JwtProperties;
import com.orvigas.security.config.SecurityProperties;
import com.orvigas.security.support.JwtTestUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

/**
 * Unit tests for JWT signing and verification.
 *
 * @author orvigas@gmail.com
 */
class JwtServiceTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-long-ok";

    private final JwtService jwtService = new JwtService(properties());

    @Test
    void createsVerifiableToken() {
        String token = jwtService.createToken("user", List.of("USER")).block();

        StepVerifier.create(jwtService.parseToken(token))
                .assertNext(claims -> {
                    assertThat(claims.getSubject()).isEqualTo("user");
                    assertThat(claims.get("roles")).asInstanceOf(LIST).containsExactly("USER");
                    assertThat(claims.getIssuer()).isEqualTo("test-issuer");
                    assertThat(claims.getAudience()).containsExactly("test-audience");
                })
                .verifyComplete();
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.createToken("user", List.of("USER")).block();
        String tampered = JwtTestUtils.tamperPayload(token);

        StepVerifier.create(jwtService.parseToken(tampered))
                .expectError()
                .verify();
    }

    @Test
    void rejectsExpiredToken() {
        String expired = buildExpiredToken();

        StepVerifier.create(jwtService.parseToken(expired))
                .expectError(ExpiredJwtException.class)
                .verify();
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "different-secret-key-must-be-at-least-256-bits-long".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("user")
                .signWith(otherKey, Jwts.SIG.HS256)
                .compact();

        StepVerifier.create(jwtService.parseToken(token))
                .expectError()
                .verify();
    }

    private static SecurityProperties properties() {
        JwtProperties jwt = new JwtProperties(SECRET, Duration.ofMinutes(15), "test-issuer", "test-audience");
        return new SecurityProperties(jwt, Map.of());
    }

    private String buildExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date past = new Date(System.currentTimeMillis() - 1000);
        return Jwts.builder()
                .subject("user")
                .issuedAt(past)
                .expiration(past)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
