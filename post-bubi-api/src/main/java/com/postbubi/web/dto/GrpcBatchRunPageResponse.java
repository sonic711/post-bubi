package com.postbubi.web.dto;

import java.util.List;

public record GrpcBatchRunPageResponse(List<GrpcBatchRunResponse> items, Integer page, Integer size, Long totalItems) {
}
