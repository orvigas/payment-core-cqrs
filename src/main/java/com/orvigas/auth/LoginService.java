package com.orvigas.auth;

import com.orvigas.security.config.SecurityProperties;
import com.orvigas.security.config.UserProperties;
import com.orvigas.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Stub authentication service that validates configured credentials and issues
 * a JWT access token.
 *
 * @author orvigas@gmail.com
 */
@Service
@RequiredArgsConstructor
public class LoginService {

    private final SecurityProperties securityProperties;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Authenticates the caller and returns a signed access token.
     *
     * <p>Returns the same generic failure for unknown users and wrong passwords
     * to avoid leaking which one occurred.
     *
     * @param request the login request
     * @return a token response
     * @throws BadCredentialsException if the credentials are invalid
     */
    public Mono<LoginResponse> authenticate(LoginRequest request) {
        return Mono.justOrEmpty(findUser(request.username()))
                .switchIfEmpty(Mono.defer(this::authenticationFailed))
                .filter(this::hasPassword)
                .switchIfEmpty(Mono.defer(this::authenticationFailed))
                .flatMap(user -> verifyPassword(request.password(), user.password())
                        .flatMap(isMatch -> isMatch ? issueToken(user) : authenticationFailedResponse()));
    }

    private UserProperties findUser(String username) {
        return securityProperties.users().values().stream()
                .filter(user -> user.username().equals(username))
                .findFirst()
                .orElse(null);
    }

    private boolean hasPassword(UserProperties user) {
        return user.password() != null && !user.password().isBlank();
    }

    private Mono<Boolean> verifyPassword(String rawPassword, String encodedPassword) {
        return Mono.fromCallable(() -> passwordEncoder.matches(rawPassword, encodedPassword))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<LoginResponse> issueToken(UserProperties user) {
        return jwtService.createToken(user.username(), user.roles())
                .map(token -> new LoginResponse(token, "Bearer", securityProperties.jwt().expiration().toSeconds()));
    }

    private Mono<UserProperties> authenticationFailed() {
        return Mono.error(new BadCredentialsException("Authentication failed"));
    }

    private Mono<LoginResponse> authenticationFailedResponse() {
        return Mono.error(new BadCredentialsException("Authentication failed"));
    }
}
