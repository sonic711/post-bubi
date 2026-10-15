package com.postbubi.fieldrule;

public record FieldRule(
        String id,
        String name,
        Boolean enabled,
        String color,
        String action,
        String targetType,
        Integer start,
        Integer end,
        String path
) {
}
