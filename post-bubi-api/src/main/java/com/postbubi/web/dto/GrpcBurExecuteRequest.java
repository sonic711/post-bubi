package com.postbubi.web.dto;

public record GrpcBurExecuteRequest(
        String executionId,
        String host,
        Integer port,
        Integer timeoutMillis,
        Boolean plaintext,
        Boolean ignoreTlsVerification,
        String metadataText,
        String protoId,
        String serviceName,
        String methodName,
        String tcpipHeaderHex,
        String mcsHeader,
        String basicLabel,
        String textArea,
        GrpcBurSettings settings
) {
    public record GrpcBurSettings(
            Integer mcsHeaderLength,
            Integer basicLabelLength,
            Integer textAreaLength,
            Boolean padTextAreaRight
    ) {
    }
}
