package com.postbubi.web;

import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final String version;

    public HealthController(@Value("${post-bubi.version}") String version) {
        this.version = version;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "name", "post-bubi",
                "version", version,
                "time", Instant.now().toString()
        );
    }
}
