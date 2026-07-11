package com.postbubi.web.dto;

import java.time.Instant;
import java.util.List;

public record EnvironmentResponse(
        Long id,
        String name,
        List<EnvironmentVariable> variables,
        Instant createdAt,
        Instant updatedAt
) {
}
