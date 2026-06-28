package com.postbubi.web.dto;

import java.time.Instant;

public record ProtoListResponse(
        String protoId,
        String filename,
        long sizeBytes,
        Instant updatedAt
) {
}
