package com.postbubi.web.dto;

public record FolderCreateRequest(
        Long collectionId,
        Long parentFolderId,
        String name,
        Integer sortOrder
) {
}
