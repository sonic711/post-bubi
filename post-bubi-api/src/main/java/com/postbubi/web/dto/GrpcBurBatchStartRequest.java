package com.postbubi.web.dto;

import com.postbubi.domain.HttpBatchMode;

public record GrpcBurBatchStartRequest(
        Long requestId,
        GrpcBurExecuteRequest grpcBurRequest,
        HttpBatchMode mode,
        Integer totalCount,
        Integer maxConcurrency,
        Integer intervalMillis,
        Integer deadlineMillis
) {
}
