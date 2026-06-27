package com.postbubi.web.error;

import java.util.Map;

public record ApiErrorResponse(
        String code,
        String message,
        Map<String, Object> details
) {
}
