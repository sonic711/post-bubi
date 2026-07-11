package com.postbubi.web.dto;

import java.util.List;

public record EnvironmentCreateRequest(
        String name,
        List<EnvironmentVariable> variables
) {
}
