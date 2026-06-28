package com.postbubi.web.dto;

import java.time.Instant;

public record RequestHistoryResponse(
        Long id,
        Long requestId,
        String method,
        String url,
        Integer statusCode,
        Long durationMillis,
        Long sizeBytes,
        Boolean success,
        String errorMessage,
        String requestJson,
        String responseBodyPreview,
        Instant createdAt
) {
}
