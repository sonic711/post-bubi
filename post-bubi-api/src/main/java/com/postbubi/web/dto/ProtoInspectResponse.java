package com.postbubi.web.dto;

import java.util.List;

public record ProtoInspectResponse(
        String protoId,
        String filename,
        String packageName,
        List<String> imports,
        List<String> messages,
        List<ProtoServiceDefinition> services
) {
}
