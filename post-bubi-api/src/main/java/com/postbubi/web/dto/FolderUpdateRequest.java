package com.postbubi.web.dto;

public record FolderUpdateRequest(
        Long parentFolderId,
        String name,
        Integer sortOrder
) {
}
