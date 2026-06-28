package com.postbubi.web.dto;

public record FileUploadResponse(
        String fileId,
        String originalFilename,
        String storedFilename,
        String contentType,
        long sizeBytes
) {
}
