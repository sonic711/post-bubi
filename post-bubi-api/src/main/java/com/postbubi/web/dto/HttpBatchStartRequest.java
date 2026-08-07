package com.postbubi.web.dto;

import com.postbubi.domain.HttpBatchMode;

public record HttpBatchStartRequest(
        HttpExecuteRequest httpRequest,
        HttpBatchMode mode,
        Integer totalCount,
        Integer maxConcurrency,
        Integer intervalMillis,
        Integer deadlineMillis
) {
}
