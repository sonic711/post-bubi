package com.postbubi.web.dto;

import java.time.Instant;
import java.util.List;

import com.postbubi.domain.HttpBatchItemStatus;

public record GrpcBatchItemResponse(
        Long id,
        Integer sequenceNumber,
        HttpBatchItemStatus status,
        String statusCode,
        String statusDescription,
        Long durationMillis,
        Long sizeBytes,
        String errorMessage,
        List<HttpNameValue> responseMetadata,
        String responseBodyPreview,
        List<GrpcBurExecuteResponse.GrpcBurDecodedPayload> decodedPayloads,
        Instant startedAt,
        Instant completedAt
) {
}
