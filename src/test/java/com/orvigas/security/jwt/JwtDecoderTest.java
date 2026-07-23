package com.orvigas.security.jwt;

import com.orvigas.security.config.JwtProperties;
import com.orvigas.security.config.SecurityProperties;
import com.orvigas.security.support.JwtTestUtils;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Spring Security reactive JWT decoder backed by JJWT.
 *
 * @author orvigas@gmail.com
 */
class JwtDecoderTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-long-ok";

    private final JwtService jwtService = new JwtService(properties());
    private final JwtDecoder jwtDecoder = new JwtDecoder(jwtService);

    @Test
    void decodesValidToken() {
        String token = jwtService.createToken("user", List.of("USER"), "00000000-0000-0000-0000-000000000001").block();

        StepVerifier.create(jwtDecoder.decode(token))
                .assertNext(jwt -> {
                    assertThat(jwt.getSubject()).isEqualTo("user");
                    assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
                    assertThat(jwt.getHeaders()).containsKey("alg");
                })
                .verifyComplete();
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.createToken("user", List.of("USER"), "00000000-0000-0000-0000-000000000001").block();
        String tampered = JwtTestUtils.tamperPayload(token);

        StepVerifier.create(jwtDecoder.decode(tampered))
                .expectError()
                .verify();
    }

    @Test
    void rejectsExpiredToken() {
        String expired = buildExpiredToken();

        StepVerifier.create(jwtDecoder.decode(expired))
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
