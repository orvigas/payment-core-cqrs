package com.orvigas.support.docker;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Answers the question T-001 asks explicitly: what happens when Docker isn't
 * reachable, and is the default behavior already clear enough?
 *
 * <p>Testcontainers itself already fails fast with a specific, actionable
 * {@code IllegalStateException} - "Could not find a valid Docker environment"
 * plus a link to its troubleshooting page - rather than hanging or timing
 * out with no explanation; Spring Boot layers a
 * {@code DockerEnvironmentNotFoundFailureAnalyzer} on top that turns the same
 * exception into a readable context-startup failure. Neither of those needed
 * writing; this test exists to pin the contract down so a Testcontainers or
 * Spring Boot upgrade that silently changes it (a bare timeout, a hang, a
 * generic exception) gets caught here instead of confusing whoever hits it
 * next in a Docker-less environment.
 *
 * <p>The check runs in a forked JVM rather than in-process: this test suite
 * always has a real, working Docker daemon available (that's how every other
 * test in this module runs), and {@code TestcontainersConfiguration} caches
 * the first environment it finds for the lifetime of the JVM. A forked
 * process with its own {@code user.home} starts uncached, so asking it to
 * resolve a strategy from an empty candidate list deterministically exercises
 * the "nothing works" path without needing to touch the real Docker daemon.
 *
 * @author orvigas@gmail.com
 */
class DockerUnavailableFailureModeTest {

    private static final Duration FORK_TIMEOUT = Duration.ofSeconds(30);

    @Test
    void failsFastWithASpecificMessageInsteadOfHangingOrTimingOut() throws Exception {
        Path isolatedHome = Files.createTempDirectory("docker-probe-home");
        Process process = startProbe(isolatedHome);

        boolean finished = process.waitFor(FORK_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertThat(finished)
                .as("resolving with no Docker strategy available should fail fast, not hang past %s", FORK_TIMEOUT)
                .isTrue();

        String output = new String(process.getInputStream().readAllBytes());
        assertThat(output)
                .as("full probe output was:%n%s", output)
                .contains("PROBE_RESULT=NO_STRATEGY_FOUND")
                .contains("Could not find a valid Docker environment");
    }

    private Process startProbe(Path isolatedHome) throws IOException {
        String javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");

        List<String> command = List.of(
                javaBinary,
                "-Duser.home=" + isolatedHome,
                "-cp", classpath,
                DockerStrategyProbe.class.getName());

        return new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
    }
}
