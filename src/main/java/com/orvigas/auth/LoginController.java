package com.orvigas.auth;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Authentication endpoint for obtaining JWT access tokens.
 *
 * @author orvigas@gmail.com
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "JWT token issuance")
public class LoginController {

    private final LoginService loginService;

    /**
     * Authenticates a configured user and returns a signed JWT.
     *
     * @param request the login credentials
     * @return the issued token
     */
    @PostMapping("/login")
    @RateLimiter(name = "login")
    @Operation(summary = "Authenticate and obtain a JWT access token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token issued",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "429", description = "Too many login attempts")
    })
    public Mono<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return loginService.authenticate(request);
    }
}
