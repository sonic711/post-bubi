package com.postbubi.web.dto;

import java.util.List;

public record GrpcExecuteResponse(
        String statusCode,
        String statusDescription,
        Long durationMillis,
        List<HttpNameValue> metadata,
        String body,
        String errorMessage
) {
}
