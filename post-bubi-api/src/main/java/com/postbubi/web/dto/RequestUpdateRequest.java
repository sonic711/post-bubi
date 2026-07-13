package com.postbubi.web.dto;

import com.postbubi.domain.RequestType;

public record RequestUpdateRequest(
        Long collectionId,
        Long folderId,
        RequestType type,
        String name,
        Integer sortOrder,
        String payloadJson
) {
}
