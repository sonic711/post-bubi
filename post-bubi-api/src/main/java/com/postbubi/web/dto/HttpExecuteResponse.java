package com.postbubi.web.dto;

import java.util.List;

public record HttpExecuteResponse(
        Integer statusCode,
        String reasonPhrase,
        Long durationMillis,
        Long sizeBytes,
        List<HttpNameValue> headers,
        String body,
        Boolean bodyBase64Encoded
) {
}
