package com.postbubi.grpcbur;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.postbubi.execution.ExecutionCancellationService.ExecutionHandle;
import com.postbubi.grpc.GrpcExecuteService;
import com.postbubi.proto.ProtoStorageService;
import com.postbubi.web.dto.GrpcBurExecuteRequest;
import com.postbubi.web.dto.GrpcBurExecuteResponse;
import com.postbubi.web.dto.GrpcExecuteRequest;
import com.postbubi.web.dto.GrpcExecuteResponse;
import com.postbubi.web.dto.HttpNameValue;
import com.postbubi.web.dto.ProtoInspectResponse;
import com.postbubi.web.dto.ProtoRpcDefinition;
import com.postbubi.web.dto.ProtoServiceDefinition;
import com.postbubi.web.error.ApiException;

@Service
public class GrpcBurExecuteService {

    private static final String DEFAULT_SERVICE_NAME = "com.bot.fsap.model.grpc.common.Service";
    private static final String DEFAULT_METHOD_NAME = "rpcPeriphery";
    private static final String DEFAULT_TCPIP_HEADER_HEX = "0F 0F 0F 00 02 65 01 F0 F0 F0 0B 0F";
    private static final int DEFAULT_MCS_HEADER_LENGTH = 72;
    private static final int DEFAULT_BASIC_LABEL_LENGTH = 158;
    private static final int DEFAULT_TIMEOUT_MILLIS = 30000;
    private static final int MIN_TIMEOUT_MILLIS = 1;
    private static final int MAX_TIMEOUT_MILLIS = 300000;

    private final BurCodecService burCodecService;
    private final GrpcExecuteService grpcExecuteService;
    private final ProtoStorageService protoStorageService;
    private final ObjectMapper objectMapper;

    public GrpcBurExecuteService(
            BurCodecService burCodecService,
            GrpcExecuteService grpcExecuteService,
            ProtoStorageService protoStorageService,
            ObjectMapper objectMapper
    ) {
        this.burCodecService = burCodecService;
        this.grpcExecuteService = grpcExecuteService;
        this.protoStorageService = protoStorageService;
        this.objectMapper = objectMapper;
    }

    public GrpcBurExecuteResponse execute(GrpcBurExecuteRequest request, ExecutionHandle execution) {
        ComposedPayload composedPayload = compose(request);
        GrpcExecuteRequest grpcRequest = new GrpcExecuteRequest(
                request.executionId(),
                request.host(),
                request.port(),
                request.plaintext() == null ? Boolean.TRUE : request.plaintext(),
                request.ignoreTlsVerification(),
                parseMetadata(request.metadataText()),
                resolveProtoId(request.protoId(), defaultText(request.serviceName(), DEFAULT_SERVICE_NAME), defaultText(request.methodName(), DEFAULT_METHOD_NAME)),
                defaultText(request.serviceName(), DEFAULT_SERVICE_NAME),
                defaultText(request.methodName(), DEFAULT_METHOD_NAME),
                requestBody(composedPayload.payload()),
                normalizeTimeout(request.timeoutMillis())
        );
        GrpcExecuteResponse grpcResponse = grpcExecuteService.execute(grpcRequest, execution);
        return new GrpcBurExecuteResponse(
                grpcResponse.statusCode(),
                grpcResponse.statusDescription(),
                grpcResponse.durationMillis(),
                grpcResponse.metadata(),
                grpcResponse.body(),
                grpcResponse.errorMessage(),
                composedPayload.preview(),
                decodePayloads(grpcResponse.body())
        );
    }

    public GrpcBurExecuteResponse.GrpcBurRequestPreview preview(GrpcBurExecuteRequest request) {
        return compose(request).preview();
    }

    private ComposedPayload compose(GrpcBurExecuteRequest request) {
        GrpcBurExecuteRequest.GrpcBurSettings settings = request.settings();
        int mcsHeaderLength = settings == null || settings.mcsHeaderLength() == null
                ? DEFAULT_MCS_HEADER_LENGTH
                : settings.mcsHeaderLength();
        int basicLabelLength = settings == null || settings.basicLabelLength() == null
                ? DEFAULT_BASIC_LABEL_LENGTH
                : settings.basicLabelLength();
        Integer textAreaLength = settings == null ? null : settings.textAreaLength();
        boolean padTextAreaRight = settings == null || !Boolean.FALSE.equals(settings.padTextAreaRight());

        byte[] tcpipHeader = decodeHex(defaultText(request.tcpipHeaderHex(), DEFAULT_TCPIP_HEADER_HEX), "TCPIP Header");
        String mcsHeader = normalizeFixedText(defaultText(request.mcsHeader(), ""), mcsHeaderLength, "MCS Header");
        String basicLabel = normalizeFixedText(defaultText(request.basicLabel(), ""), basicLabelLength, "Basic Label");
        String textArea = normalizeTextArea(defaultText(request.textArea(), ""), textAreaLength, padTextAreaRight);

        byte[] mcsHeaderBytes = mcsHeader.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] basicLabelBytes = burCodecService.encode(basicLabel);
        byte[] textAreaBytes = burCodecService.encode(textArea);
        byte[] payload = concat(tcpipHeader, mcsHeaderBytes, basicLabelBytes, textAreaBytes);

        GrpcBurExecuteResponse.GrpcBurRequestPreview preview = new GrpcBurExecuteResponse.GrpcBurRequestPreview(
                burCodecService.codecName(),
                tcpipHeader.length,
                mcsHeaderBytes.length,
                basicLabelBytes.length,
                textAreaBytes.length,
                payload.length,
                encodeHex(payload),
                burCodecService.decode(payload)
        );
        return new ComposedPayload(payload, preview);
    }

    private String requestBody(byte[] payload) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "payload", Map.of(
                            "charsets", "BUR",
                            "format", "TEXT",
                            "data", Base64.getEncoder().encodeToString(payload)
                    )
            ));
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "GRPC_BUR_REQUEST_BUILD_FAILED", "gRPC BUR request body 建立失敗。");
        }
    }

    private List<GrpcBurExecuteResponse.GrpcBurDecodedPayload> decodePayloads(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode payloadNode = root.path("payload");
            if (!payloadNode.isObject()) {
                return List.of();
            }
            List<GrpcBurExecuteResponse.GrpcBurDecodedPayload> result = new ArrayList<>();
            payloadNode.fields().forEachRemaining(entry -> result.add(decodePayload(entry.getKey(), entry.getValue())));
            return result;
        } catch (Exception exception) {
            return List.of(new GrpcBurExecuteResponse.GrpcBurDecodedPayload(
                    "",
                    "",
                    "",
                    0,
                    "",
                    "",
                    "Response body 不是可解析的 PeripheryResponse JSON：" + exception.getMessage()
            ));
        }
    }

    private GrpcBurExecuteResponse.GrpcBurDecodedPayload decodePayload(String key, JsonNode payload) {
        try {
            String data = payload.path("data").asText("");
            byte[] bytes = Base64.getDecoder().decode(data);
            return new GrpcBurExecuteResponse.GrpcBurDecodedPayload(
                    key,
                    payload.path("charsets").asText(""),
                    payload.path("format").asText(""),
                    bytes.length,
                    encodeHex(bytes),
                    burCodecService.decode(bytes),
                    ""
            );
        } catch (Exception exception) {
            return new GrpcBurExecuteResponse.GrpcBurDecodedPayload(
                    key,
                    payload.path("charsets").asText(""),
                    payload.path("format").asText(""),
                    0,
                    "",
                    "",
                    "Payload 解碼失敗：" + exception.getMessage()
            );
        }
    }

    private List<HttpNameValue> parseMetadata(String metadataText) {
        if (metadataText == null || metadataText.isBlank()) {
            return List.of();
        }
        List<HttpNameValue> result = new ArrayList<>();
        for (String line : metadataText.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int delimiter = trimmed.indexOf('=');
            if (delimiter < 0) {
                delimiter = trimmed.indexOf(':');
            }
            if (delimiter <= 0) {
                continue;
            }
            result.add(new HttpNameValue(trimmed.substring(0, delimiter).trim(), trimmed.substring(delimiter + 1).trim(), true));
        }
        return result;
    }

    private String resolveProtoId(String requestedProtoId, String serviceName, String methodName) {
        String normalized = blankToNull(requestedProtoId);
        if (normalized != null) {
            return normalized;
        }
        for (var proto : protoStorageService.list()) {
            try {
                ProtoInspectResponse inspect = protoStorageService.inspect(proto.protoId());
                String packageName = inspect.packageName() == null || inspect.packageName().isBlank() ? "" : inspect.packageName() + ".";
                for (ProtoServiceDefinition service : inspect.services()) {
                    String fullServiceName = packageName + service.name();
                    if (!serviceName.equals(fullServiceName)) {
                        continue;
                    }
                    for (ProtoRpcDefinition method : service.methods()) {
                        if (methodName.equals(method.name())) {
                            return proto.protoId();
                        }
                    }
                }
            } catch (Exception ignored) {
                // Ignore invalid proto candidates; execute will fall back to reflection if no match is found.
            }
        }
        return null;
    }

    private String normalizeFixedText(String value, int length, String label) {
        if (length <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GRPC_BUR_LENGTH_INVALID", label + " 長度設定必須大於 0。");
        }
        if (value.length() > length) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GRPC_BUR_TEXT_TOO_LONG", label + " 長度不可超過 " + length + " 字元。");
        }
        return value + " ".repeat(length - value.length());
    }

    private String normalizeTextArea(String value, Integer length, boolean padRight) {
        if (length == null || length <= 0) {
            return value;
        }
        if (value.length() > length) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GRPC_BUR_TEXT_TOO_LONG", "Text Area 長度不可超過 " + length + " 字元。");
        }
        return padRight ? value + " ".repeat(length - value.length()) : value;
    }

    private int normalizeTimeout(Integer timeoutMillis) {
        int timeout = timeoutMillis == null ? DEFAULT_TIMEOUT_MILLIS : timeoutMillis;
        if (timeout < MIN_TIMEOUT_MILLIS || timeout > MAX_TIMEOUT_MILLIS) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "GRPC_TIMEOUT_INVALID",
                    "Timeout 必須介於 1 至 300000 毫秒。"
            );
        }
        return timeout;
    }

    private byte[] decodeHex(String value, String label) {
        String normalized = value.replaceAll("\\s+", "");
        if (normalized.length() % 2 != 0 || !normalized.matches("[0-9A-Fa-f]*")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GRPC_BUR_HEX_INVALID", label + " hex 格式錯誤。");
        }
        byte[] result = new byte[normalized.length() / 2];
        for (int index = 0; index < normalized.length(); index += 2) {
            result[index / 2] = (byte) Integer.parseInt(normalized.substring(index, index + 2), 16);
        }
        return result;
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

    private byte[] concat(byte[]... parts) {
        int totalLength = 0;
        for (byte[] part : parts) {
            totalLength += part.length;
        }
        byte[] result = new byte[totalLength];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    private String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ComposedPayload(byte[] payload, GrpcBurExecuteResponse.GrpcBurRequestPreview preview) {
    }
}
