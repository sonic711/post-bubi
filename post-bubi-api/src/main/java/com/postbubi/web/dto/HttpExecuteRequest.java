package com.postbubi.web.dto;

import java.util.List;

public record HttpExecuteRequest(
        String executionId,
        Long requestId,
        String method,
        String url,
        List<HttpNameValue> params,
        List<HttpNameValue> headers,
        String bodyType,
        String body,
        List<HttpFormDataPart> formData,
        Integer timeoutMillis,
        Boolean followRedirects,
        Boolean ignoreSslVerification
) {
}
