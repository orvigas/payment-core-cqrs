package com.orvigas.auth;

import com.orvigas.security.config.JwtProperties;
import com.orvigas.security.config.SecurityProperties;
import com.orvigas.security.config.UserProperties;
import com.orvigas.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LoginService}'s authentication branches, isolated
 * from the Spring context with mocked collaborators so each outcome (unknown
 * user, no configured password, wrong password, success) is provable without
 * standing up the full security filter chain.
 *
 * @author orvigas@gmail.com
 */
@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final String USERNAME = "test-user";
    private static final String ENCODED_PASSWORD = "encoded-hash";
    private static final String MERCHANT_ID = "00000000-0000-0000-0000-000000000001";
    private static final String GENERIC_FAILURE_MESSAGE = "Authentication failed";

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private LoginService loginService;

    @BeforeEach
    void setUp() {
        loginService = loginServiceWithUser(
                new UserProperties(USERNAME, ENCODED_PASSWORD, List.of("USER"), MERCHANT_ID));
    }

    @Test
    void unknownUsernameFailsWithGenericMessage() {
        StepVerifier.create(loginService.authenticate(new LoginRequest("nobody", "irrelevant")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BadCredentialsException.class);
                    assertThat(error).hasMessage(GENERIC_FAILURE_MESSAGE);
                })
                .verify();
    }

    @Test
    void userWithNoConfiguredPasswordFailsClosedWithGenericMessage() {
        LoginService serviceWithNoPassword = loginServiceWithUser(
                new UserProperties(USERNAME, "", List.of("USER"), MERCHANT_ID));

        StepVerifier.create(serviceWithNoPassword.authenticate(new LoginRequest(USERNAME, "any-password")))
                .expectErrorSatisfies(error -> assertThat(error).hasMessage(GENERIC_FAILURE_MESSAGE))
                .verify();
    }

    @Test
    void wrongPasswordFailsWithGenericMessage() {
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        StepVerifier.create(loginService.authenticate(new LoginRequest(USERNAME, "wrong-password")))
                .expectErrorSatisfies(error -> assertThat(error).hasMessage(GENERIC_FAILURE_MESSAGE))
                .verify();
    }

    @Test
    void correctCredentialsIssueASignedToken() {
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtService.createToken(USERNAME, List.of("USER"), MERCHANT_ID)).thenReturn(Mono.just("signed-token"));

        StepVerifier.create(loginService.authenticate(new LoginRequest(USERNAME, "correct-password")))
                .assertNext(response -> {
                    assertThat(response.accessToken()).isEqualTo("signed-token");
                    assertThat(response.tokenType()).isEqualTo("Bearer");
                    assertThat(response.expiresIn()).isEqualTo(900L);
                })
                .verifyComplete();
    }

    private LoginService loginServiceWithUser(UserProperties user) {
        SecurityProperties securityProperties = new SecurityProperties(
                new JwtProperties("a-valid-secret-key-that-is-at-least-32-bytes-long",
                        Duration.ofMinutes(15), "issuer", "audience"),
                Map.of("default", user));
        return new LoginService(securityProperties, passwordEncoder, jwtService);
    }
}
