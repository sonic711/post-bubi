package com.postbubi.web.dto;

import java.util.List;

import com.postbubi.fieldrule.FieldRule;

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
        Boolean ignoreSslVerification,
        List<FieldRule> fieldRules,
        Integer fieldRuleIncrement
) {
}
