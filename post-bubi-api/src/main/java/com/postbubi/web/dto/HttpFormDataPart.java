package com.postbubi.web.dto;

public record HttpFormDataPart(
        String type,
        String name,
        String value,
        String fileId,
        String fileName,
        String contentType,
        Boolean enabled
) {
}
