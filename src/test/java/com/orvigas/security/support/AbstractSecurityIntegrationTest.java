package com.orvigas.security.support;

import com.orvigas.support.AbstractIntegrationTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

/**
 * Base class for security integration tests that need a configured test user.
 *
 * <p>Generates a random password and its BCrypt hash at class load time so the
 * credentials are not committed, and registers the user through the Spring
 * environment so the stub login service can authenticate it. A random
 * {@code testMerchantId} is generated the same way and embedded in every
 * token the test user is issued, so tests can assert on merchant-scoped
 * authorization without hardcoding a shared tenant id.
 *
 * <p>{@code @DirtiesContext} is required here: every subclass inherits the same
 * {@code registerTestUser} method, so Spring's context cache key treats them as
 * identical and will happily reuse one subclass's context (and its already-bound
 * password hash) for another subclass whose static fields have since been
 * overwritten with a different random password. That mismatch surfaces as a
 * spurious 401 on login. Forcing the context to close after each class prevents
 * the reuse.
 *
 * @author orvigas@gmail.com
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractSecurityIntegrationTest extends AbstractIntegrationTest {

    protected static final String TEST_USERNAME = "test-user";

    protected static String testPassword;
    protected static String testPasswordHash;
    protected static String testMerchantId;

    @DynamicPropertySource
    static void registerTestUser(DynamicPropertyRegistry registry) {
        testPassword = randomPassword();
        testPasswordHash = new BCryptPasswordEncoder(10).encode(testPassword);
        testMerchantId = UUID.randomUUID().toString();
        registry.add("com.orvigas.security.users.test.username", () -> TEST_USERNAME);
        registry.add("com.orvigas.security.users.test.password", () -> testPasswordHash);
        registry.add("com.orvigas.security.users.test.roles[0]", () -> "USER");
        registry.add("com.orvigas.security.users.test.merchant-id", () -> testMerchantId);
    }

    private static String randomPassword() {
        return UUID.randomUUID().toString().replace("-", "") + "A1!";
    }
}
