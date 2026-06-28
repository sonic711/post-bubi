package com.postbubi.web.dto;

import java.util.List;

public record ProtoServiceDefinition(
        String name,
        List<ProtoRpcDefinition> methods
) {
}
