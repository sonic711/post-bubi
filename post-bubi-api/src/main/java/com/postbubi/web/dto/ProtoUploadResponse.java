package com.postbubi.web.dto;

public record ProtoUploadResponse(
        String protoId,
        String originalFilename,
        String storedFilename,
        long sizeBytes
) {
}
