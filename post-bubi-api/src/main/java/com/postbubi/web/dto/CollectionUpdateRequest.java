package com.postbubi.web.dto;

public record CollectionUpdateRequest(
        String name,
        String description,
        Integer sortOrder
) {
}
