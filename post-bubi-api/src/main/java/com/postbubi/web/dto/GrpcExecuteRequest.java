package com.postbubi.web.dto;

import java.util.List;

import com.postbubi.fieldrule.FieldRule;

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
        Boolean encodePayloadDataBase64,
        Integer timeoutMillis,
        List<FieldRule> fieldRules,
        Integer fieldRuleIncrement
) {
}
