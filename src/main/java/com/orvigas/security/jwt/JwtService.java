package com.orvigas.security.jwt;

import com.orvigas.security.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * Signs and verifies HMAC-SHA256 JWTs for the reactive security chain.
 *
 * <p>All signing and parsing uses JJWT's {@code parseSignedClaims()} path
 * exclusively; the {@code alg} header is never trusted to select the algorithm.
 *
 * @author orvigas@gmail.com
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final SecurityProperties securityProperties;

    /**
     * Creates the service from security properties.
     *
     * @param securityProperties the security configuration
     */
    public JwtService(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
        this.signingKey = Keys.hmacShaKeyFor(
                securityProperties.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a signed access token for the given subject and roles.
     *
     * @param username the token subject
     * @param roles    the roles to embed as a claim
     * @return the compact signed JWT
     */
    public Mono<String> createToken(String username, List<String> roles) {
        return Mono.fromCallable(() -> buildToken(username, roles))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String buildToken(String username, List<String> roles) {
        Date now = new Date();
        long expirationMillis = securityProperties.jwt().expiration().toMillis();
        Date expiration = new Date(now.getTime() + expirationMillis);
        return Jwts.builder()
                .subject(username)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiration)
                .issuer(securityProperties.jwt().issuer())
                .audience()
                .add(securityProperties.jwt().audience())
                .and()
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Verifies and parses a signed token.
     *
     * @param token the compact JWT
     * @return the verified claims
     * @throws io.jsonwebtoken.JwtException if the token is invalid, expired, or tampered
     */
    public Mono<Claims> parseToken(String token) {
        return parseSignedToken(token).map(Jws::getPayload);
    }

    /**
     * Verifies and parses a signed token, returning the full JWS including the header.
     *
     * @param token the compact JWT
     * @return the verified JWS
     * @throws io.jsonwebtoken.JwtException if the token is invalid, expired, or tampered
     */
    public Mono<Jws<Claims>> parseSignedToken(String token) {
        return Mono.fromCallable(() -> Jwts.parser()
                        .verifyWith(signingKey)
                        .build()
                        .parseSignedClaims(token))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
