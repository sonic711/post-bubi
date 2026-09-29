package com.postbubi.web.dto;

import com.postbubi.domain.HttpBatchMode;

public record GrpcBatchStartRequest(
        Long requestId,
        GrpcExecuteRequest grpcRequest,
        HttpBatchMode mode,
        Integer totalCount,
        Integer maxConcurrency,
        Integer intervalMillis,
        Integer deadlineMillis
) {
}
