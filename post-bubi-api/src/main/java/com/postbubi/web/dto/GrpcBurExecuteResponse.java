package com.postbubi.web.dto;

import java.util.List;

public record GrpcBurExecuteResponse(
        String statusCode,
        String statusDescription,
        long durationMillis,
        List<HttpNameValue> metadata,
        String body,
        String errorMessage,
        GrpcBurRequestPreview requestPreview,
        List<GrpcBurDecodedPayload> decodedPayloads
) {
    public record GrpcBurRequestPreview(
            String codec,
            int tcpipHeaderLength,
            int mcsHeaderLength,
            int basicLabelLength,
            int textAreaLength,
            int payloadLength,
            String payloadHex,
            String decodedText
    ) {
    }

    public record GrpcBurDecodedPayload(
            String key,
            String charsets,
            String format,
            int length,
            String hex,
            String text,
            String error
    ) {
    }
}
