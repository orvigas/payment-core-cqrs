package com.orvigas.security.support;

import com.orvigas.support.AbstractIntegrationTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

/**
 * Base class for security integration tests that need a configured test user.
 *
 * <p>Generates a random password and its BCrypt hash at class load time so the
 * credentials are not committed, and registers the user through the Spring
 * environment so the stub login service can authenticate it.
 *
 * @author orvigas@gmail.com
 */
public abstract class AbstractSecurityIntegrationTest extends AbstractIntegrationTest {

    protected static final String TEST_USERNAME = "test-user";

    protected static String testPassword;
    protected static String testPasswordHash;

    @DynamicPropertySource
    static void registerTestUser(DynamicPropertyRegistry registry) {
        testPassword = randomPassword();
        testPasswordHash = new BCryptPasswordEncoder(10).encode(testPassword);
        registry.add("com.orvigas.security.users.test.username", () -> TEST_USERNAME);
        registry.add("com.orvigas.security.users.test.password", () -> testPasswordHash);
        registry.add("com.orvigas.security.users.test.roles[0]", () -> "USER");
    }

    private static String randomPassword() {
        return UUID.randomUUID().toString().replace("-", "") + "A1!";
    }
}
