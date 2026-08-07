package com.postbubi.web.dto;

import java.util.List;

public record HttpBatchRunPageResponse(
        List<HttpBatchRunResponse> items,
        Integer page,
        Integer size,
        Long totalItems
) {
}
