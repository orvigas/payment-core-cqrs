package com.orvigas.security.config;

import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.spring6.fallback.FallbackExecutor;
import io.github.resilience4j.spring6.ratelimiter.configure.RateLimiterAspect;
import io.github.resilience4j.spring6.ratelimiter.configure.RateLimiterAspectExt;
import io.github.resilience4j.spring6.ratelimiter.configure.RateLimiterConfigurationProperties;
import io.github.resilience4j.spring6.spelresolver.SpelResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Manual registration of the Resilience4j {@link RateLimiterAspect}.
 *
 * <p>Resilience4j 2.4.0's Spring Boot 4 auto-configuration only creates the
 * main aspect when RxJava 3 is on the classpath, even though the reactor
 * extension is created for WebFlux. This bean fills the gap for reactive
 * WebFlux services using the reactor extension.
 *
 * @author orvigas@gmail.com
 */
@Configuration
public class RateLimiterAspectConfig {

    /**
     * Creates the rate limiter aspect unless Resilience4j already defined it.
     *
     * @param rateLimiterRegistry             the rate limiter registry
     * @param rateLimiterConfigurationProperties the rate limiter configuration properties
     * @param rateLimiterAspectExtList        the available aspect extensions, including the reactor one
     * @param fallbackExecutor                the fallback executor
     * @param spelResolver                    the SpEL resolver
     * @return the rate limiter aspect
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimiterAspect rateLimiterAspect(
            RateLimiterRegistry rateLimiterRegistry,
            RateLimiterConfigurationProperties rateLimiterConfigurationProperties,
            List<RateLimiterAspectExt> rateLimiterAspectExtList,
            FallbackExecutor fallbackExecutor,
            SpelResolver spelResolver) {
        return new RateLimiterAspect(
                rateLimiterRegistry,
                rateLimiterConfigurationProperties,
                rateLimiterAspectExtList,
                fallbackExecutor,
                spelResolver);
    }
}
