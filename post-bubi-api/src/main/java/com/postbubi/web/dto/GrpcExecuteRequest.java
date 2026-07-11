package com.postbubi.web.dto;

import java.util.List;

public record GrpcExecuteRequest(
        String executionId,
        String host,
        Integer port,
        Boolean plaintext,
        Boolean ignoreTlsVerification,
        List<HttpNameValue> metadata,
        String protoId,
        String serviceName,
        String methodName,
        String body,
        Integer timeoutMillis
) {
}
