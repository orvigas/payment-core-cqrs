package com.orvigas.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the bearer JWT scheme referenced by {@code @SecurityRequirement}
 * annotations on controllers, so Swagger UI renders an "Authorize" button
 * that can exercise the reactive JWT security chain end to end.
 *
 * @author orvigas@gmail.com
 */
@Configuration(proxyBeanMethods = false)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Access token issued by POST /auth/login")
public class OpenApiSecurityConfig {
}
