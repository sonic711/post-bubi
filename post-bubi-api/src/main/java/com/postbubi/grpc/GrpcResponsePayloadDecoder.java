package com.postbubi.grpc;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postbubi.grpcbur.BurCodecService;
import com.postbubi.web.dto.GrpcBurExecuteResponse.GrpcBurDecodedPayload;

/** Decodes response payload entries explicitly identified as BUR by the target service. */
@Service
public class GrpcResponsePayloadDecoder {

    private final ObjectMapper objectMapper;
    private final BurCodecService burCodecService;

    public GrpcResponsePayloadDecoder(ObjectMapper objectMapper, BurCodecService burCodecService) {
        this.objectMapper = objectMapper;
        this.burCodecService = burCodecService;
    }

    public List<GrpcBurDecodedPayload> decodeBurPayloads(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            JsonNode payloadNode = objectMapper.readTree(body).path("payload");
            if (!payloadNode.isObject()) {
                return List.of();
            }
            List<GrpcBurDecodedPayload> result = new ArrayList<>();
            payloadNode.fields().forEachRemaining(entry -> {
                if (isBur(entry.getValue())) {
                    result.add(decodePayload(entry.getKey(), entry.getValue()));
                }
            });
            return result;
        } catch (Exception ignored) {
            // A normal gRPC response can have any protobuf shape. No payload means no automatic BUR result.
            return List.of();
        }
    }

    private boolean isBur(JsonNode payload) {
        return "BUR".equalsIgnoreCase(payload.path("charsets").asText("").trim());
    }

    private GrpcBurDecodedPayload decodePayload(String key, JsonNode payload) {
        String charsets = payload.path("charsets").asText("");
        String format = payload.path("format").asText("");
        try {
            if (!payload.path("data").isTextual()) {
                throw new IllegalArgumentException("BUR payload.data 必須是 Base64 字串。");
            }
            String data = payload.path("data").textValue();
            byte[] bytes = Base64.getDecoder().decode(data);
            return new GrpcBurDecodedPayload(
                    key,
                    charsets,
                    format,
                    bytes.length,
                    encodeHex(bytes),
                    burCodecService.decode(bytes),
                    ""
            );
        } catch (Exception exception) {
            return new GrpcBurDecodedPayload(
                    key,
                    charsets,
                    format,
                    0,
                    "",
                    "",
                    "BUR payload 解碼失敗：" + message(exception)
            );
        }
    }

    private String encodeHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte value : bytes) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(String.format("%02X", value));
        }
        return builder.toString();
    }

    private String message(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
