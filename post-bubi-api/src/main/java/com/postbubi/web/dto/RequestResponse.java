package com.postbubi.web.dto;

import java.time.Instant;

import com.postbubi.domain.RequestType;

public record RequestResponse(
        Long id,
        Long collectionId,
        Long folderId,
        RequestType type,
        String name,
        Integer sortOrder,
        String payloadJson,
        Instant createdAt,
        Instant updatedAt
) {
}
