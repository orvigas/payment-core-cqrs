package com.orvigas.security.config;

import com.orvigas.security.error.ProblemDetailsAccessDeniedHandler;
import com.orvigas.security.error.ProblemDetailsAuthenticationEntryPoint;
import com.orvigas.security.jwt.JwtDecoder;
import com.orvigas.security.jwt.JwtRoleConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Reactive security chain configuration.
 *
 * <p>Stateless JWT bearer authentication on all routes except the public
 * allowlist. The decoder is backed by JJWT and never trusts the {@code alg}
 * header.
 *
 * @author orvigas@gmail.com
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(SecurityProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/auth/login",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/webjars/**",
            "/favicon.ico"
    };

    private final JwtDecoder jwtDecoder;
    private final JwtRoleConverter jwtRoleConverter;
    private final ProblemDetailsAuthenticationEntryPoint authenticationEntryPoint;
    private final ProblemDetailsAccessDeniedHandler accessDeniedHandler;

    /**
     * Builds the reactive security filter chain.
     *
     * @param http the server security configurer
     * @return the configured filter chain
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtDecoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtRoleConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }
}
