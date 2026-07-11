package com.postbubi.web.dto;

import java.util.List;

public record EnvironmentUpdateRequest(
        String name,
        List<EnvironmentVariable> variables
) {
}
