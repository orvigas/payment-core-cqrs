package com.orvigas.support.docker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.dockerclient.DockerClientProviderStrategy;

import java.util.Collections;

/**
 * Standalone entry point that asks Testcontainers to resolve a Docker client
 * from an empty strategy candidate list, forcing the "no environment found"
 * path deterministically. Run only as a forked child JVM from
 * {@link DockerUnavailableFailureModeTest} - never invoked from application
 * or other test code.
 *
 * <p>This has to be an out-of-process probe rather than a direct call from
 * the test method: {@code TestcontainersConfiguration} is a JVM-wide
 * singleton that memoizes the first Docker environment it finds, and by the
 * time this test class runs, {@link com.orvigas.support.AbstractIntegrationTest}
 * has almost certainly already resolved and cached a working one in the same
 * JVM. A forked process with an isolated {@code user.home} starts with a
 * clean slate, so the empty candidate list is guaranteed to fail rather than
 * fall back to whatever the real environment already discovered.
 *
 * @author orvigas@gmail.com
 */
public final class DockerStrategyProbe {

    private static final Logger log = LoggerFactory.getLogger(DockerStrategyProbe.class);

    /** Marker line the parent test greps for; keeps the assertion independent of log formatting. */
    static final String FAILURE_MARKER = "PROBE_RESULT=NO_STRATEGY_FOUND";

    private DockerStrategyProbe() {
    }

    /**
     * Attempts strategy resolution with no candidates and reports the outcome
     * through SLF4J, since the parent process reads this JVM's console
     * output to determine the result.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        try {
            DockerClientProviderStrategy.getFirstValidStrategy(Collections.emptyList());
            log.info("PROBE_RESULT=UNEXPECTED_SUCCESS");
        } catch (IllegalStateException expected) {
            log.info(FAILURE_MARKER);
            log.info("PROBE_MESSAGE={}", expected.getMessage());
        }
    }
}
