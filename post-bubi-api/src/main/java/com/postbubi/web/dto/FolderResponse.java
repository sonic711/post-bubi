package com.postbubi.web.dto;

import java.time.Instant;

public record FolderResponse(
        Long id,
        Long collectionId,
        Long parentFolderId,
        String name,
        Integer sortOrder,
        Instant createdAt,
        Instant updatedAt
) {
}
