package com.postbubi.web.dto;

import java.time.Instant;

import com.postbubi.domain.HttpBatchMode;
import com.postbubi.domain.HttpBatchRunStatus;

public record HttpBatchRunResponse(
        Long id,
        Long requestId,
        HttpBatchMode mode,
        HttpBatchRunStatus status,
        Integer totalCount,
        Integer maxConcurrency,
        Integer intervalMillis,
        Integer deadlineMillis,
        Integer dispatchedCount,
        Integer inProgressCount,
        Integer successCount,
        Integer failedCount,
        Integer cancelledCount,
        Integer notDispatchedCount,
        Long durationMillis,
        Long averageResponseMillis,
        Long fastestResponseMillis,
        Long slowestResponseMillis,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {
}
