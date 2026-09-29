package com.postbubi.web.dto;

import java.util.List;

public record GrpcBatchItemPageResponse(List<GrpcBatchItemResponse> items, Integer page, Integer size, Long totalItems) {
}
