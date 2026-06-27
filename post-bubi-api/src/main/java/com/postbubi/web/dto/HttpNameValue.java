package com.postbubi.web.dto;

public record HttpNameValue(
        String name,
        String value,
        Boolean enabled
) {
}
