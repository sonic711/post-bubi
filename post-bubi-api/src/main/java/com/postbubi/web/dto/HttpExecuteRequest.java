package com.postbubi.web.dto;

import java.util.List;

public record HttpExecuteRequest(
        String method,
        String url,
        List<HttpNameValue> params,
        List<HttpNameValue> headers,
        String bodyType,
        String body,
        Integer timeoutMillis,
        Boolean followRedirects,
        Boolean ignoreSslVerification
) {
}
