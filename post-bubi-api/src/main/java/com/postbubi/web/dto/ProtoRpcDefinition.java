package com.postbubi.web.dto;

public record ProtoRpcDefinition(
        String name,
        String requestType,
        String responseType,
        boolean clientStreaming,
        boolean serverStreaming
) {
}
