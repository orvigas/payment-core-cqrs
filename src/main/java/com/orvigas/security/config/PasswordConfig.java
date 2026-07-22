package com.orvigas.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Provides the password encoder used for login credential verification.
 *
 * @author orvigas@gmail.com
 */
@Configuration
public class PasswordConfig {

    /**
     * BCrypt with the work factor defined in the security policy.
     *
     * @return a BCrypt encoder
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
