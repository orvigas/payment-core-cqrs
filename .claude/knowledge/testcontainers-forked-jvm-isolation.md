# Forking a child JVM to test "Docker unavailable" doesn't isolate DOCKER_HOST

- Date: 2026-07-22
- Affected versions/components: Testcontainers 2.0.5, any test that shells out via `ProcessBuilder` to get a clean-slate JVM

## Problem

T-001's integration test harness needed to prove that Testcontainers fails fast with a clear message when no Docker environment is reachable, without actually breaking Docker on the machine running the suite. The approach: fork a child JVM (`DockerStrategyProbe`) with an isolated `user.home` (so it can't read the real `~/.testcontainers.properties`) and call `DockerClientProviderStrategy.getFirstValidStrategy(Collections.emptyList())`, expecting it to fail immediately since no candidate strategies were passed in.

Code review caught that this doesn't reliably test what it claims.

## Root cause

Two things compound:

1. `DockerClientProviderStrategy.getFirstValidStrategy(strategies)` (testcontainers 2.0.5) unconditionally prepends `TestcontainersHostPropertyClientProviderStrategy` and `EnvironmentAndSystemPropertyClientProviderStrategy` ahead of whatever list you pass in — passing `emptyList()` does not mean "no strategies will be tried." The premise that an empty argument list produces a guaranteed-empty candidate set is wrong.
2. `EnvironmentAndSystemPropertyClientProviderStrategy` becomes applicable whenever `DOCKER_HOST` is set in the environment. `ProcessBuilder` inherits the parent process's environment by default — it does not need `docker.host` in the isolated `user.home` config file, because the env var alone is enough.

Net effect: on any machine or CI runner where `DOCKER_HOST` is exported (Colima, remote Docker contexts, some docker-in-docker CI setups), the forked probe would actually attempt a real Docker connection instead of hitting the "no valid strategy" failure path, silently testing the wrong thing. It only passed in the original environment because that machine happened to have no `DOCKER_HOST` set — that's incidental to the test environment, not a property of the mechanism.

## Diagnosis

Found by reading the actual Testcontainers 2.0.5 source (`DockerClientProviderStrategy.getFirstValidStrategy`, `EnvironmentAndSystemPropertyClientProviderStrategy`) rather than trusting the Javadoc claim of "guarantees a clean slate." The isolated `user.home` covers `~/.testcontainers.properties` but does nothing about inherited environment variables or system properties.

## Fix

Not yet applied (tracked as a follow-up, non-blocking for T-001's merge). The fix is to explicitly clear the child process's environment before launching it — `processBuilder.environment().clear()`, then re-add only what the child actually needs (e.g. `PATH`, `JAVA_HOME` equivalents already covered by using the current JVM's `java.home`) — rather than relying on isolating just the config file location.

## Prevention

Any test that forks a subprocess to get a "clean" environment must explicitly clear (not just partially override) the inherited environment and system properties, and should verify against the library's actual resolution order rather than an argument that looks like it should be authoritative (e.g., an "empty candidates list"). Don't assume a Javadoc description of isolation guarantees is accurate without checking the source of the method being isolated.

## Keywords

testcontainers, DOCKER_HOST, ProcessBuilder, environment isolation, DockerClientProviderStrategy, forked JVM test, flaky test, ryuk, docker unavailable
