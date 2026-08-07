package com.postbubi.web.dto;

import java.time.Instant;
import java.util.List;

import com.postbubi.domain.HttpBatchItemStatus;

public record HttpBatchItemResponse(
        Long id,
        Integer sequenceNumber,
        HttpBatchItemStatus status,
        Integer statusCode,
        String reasonPhrase,
        Long durationMillis,
        Long sizeBytes,
        String errorMessage,
        List<HttpNameValue> responseHeaders,
        String responseBodyPreview,
        Instant startedAt,
        Instant completedAt
) {
}
