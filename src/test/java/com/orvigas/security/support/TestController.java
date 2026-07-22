package com.orvigas.security.support;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Test-only endpoint used to prove the security chain protects non-public routes.
 *
 * @author orvigas@gmail.com
 */
@RestController
public class TestController {

    @GetMapping("/secure")
    public Mono<String> secure() {
        return Mono.just("ok");
    }
}
