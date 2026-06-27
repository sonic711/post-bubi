package com.postbubi.web.dto;

import java.time.Instant;
import java.util.List;

public record CollectionResponse(
        Long id,
        String name,
        String description,
        List<FolderResponse> folders,
        List<RequestResponse> requests,
        Instant createdAt,
        Instant updatedAt
) {
}
