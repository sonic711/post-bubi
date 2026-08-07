package com.postbubi.web.dto;

import java.util.List;

public record HttpBatchItemPageResponse(
        List<HttpBatchItemResponse> items,
        Integer page,
        Integer size,
        Long totalItems
) {
}
