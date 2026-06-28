package com.postbubi.grpc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.postbubi.web.dto.GrpcExecuteRequest;
import com.postbubi.web.dto.GrpcExecuteResponse;
import com.postbubi.web.dto.HttpNameValue;
import com.postbubi.web.error.ApiException;

@Service
public class GrpcExecuteService {

    private static final int DEFAULT_TIMEOUT_MILLIS = 30000;
    private static final int MAX_TIMEOUT_MILLIS = 300000;

    private final GrpcReflectionDescriptorResolver descriptorResolver;

    public GrpcExecuteService(GrpcReflectionDescriptorResolver descriptorResolver) {
        this.descriptorResolver = descriptorResolver;
    }

    public GrpcExecuteResponse execute(GrpcExecuteRequest request) {
        String host = requiredText(request.host(), "GRPC_HOST_REQUIRED", "Host 不可空白。");
        int port = normalizePort(request.port());
        int timeoutMillis = normalizeTimeout(request.timeoutMillis());
        String serviceName = requiredText(request.serviceName(), "GRPC_SERVICE_REQUIRED", "Service name 不可空白。");
        String methodName = requiredText(request.methodName(), "GRPC_METHOD_REQUIRED", "Method name 不可空白。");

        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(host, port);
        if (Boolean.TRUE.equals(request.plaintext())) {
            builder.usePlaintext();
        }
        ManagedChannel channel = builder.build();
        long startNanos = System.nanoTime();
        try {
            var grpcMethod = descriptorResolver.resolveMethod(channel, serviceName, methodName);
            DynamicMessage requestMessage = toDynamicMessage(grpcMethod.getInputType(), request.body());
            MethodDescriptor<DynamicMessage, DynamicMessage> methodDescriptor = MethodDescriptor
                    .<DynamicMessage, DynamicMessage>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(serviceName, methodName))
                    .setRequestMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(grpcMethod.getInputType())))
                    .setResponseMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(grpcMethod.getOutputType())))
                    .build();

            DynamicMessage response = ClientCalls.blockingUnaryCall(
                    channel,
                    methodDescriptor,
                    CallOptions.DEFAULT.withDeadlineAfter(timeoutMillis, TimeUnit.MILLISECONDS),
                    requestMessage
            );
            long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
            return new GrpcExecuteResponse(
                    Status.Code.OK.name(),
                    "",
                    durationMillis,
                    List.of(),
                    JsonFormat.printer().includingDefaultValueFields().print(response),
                    ""
            );
        } catch (ApiException exception) {
            throw exception;
        } catch (StatusRuntimeException exception) {
            long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
            return new GrpcExecuteResponse(
                    exception.getStatus().getCode().name(),
                    exception.getStatus().getDescription(),
                    durationMillis,
                    trailersToHeaders(exception.getTrailers()),
                    "",
                    exception.getMessage()
            );
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "GRPC_EXECUTE_FAILED",
                    "gRPC 請求執行失敗。",
                    java.util.Map.of("reason", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage())
            );
        } finally {
            channel.shutdownNow();
        }
    }

    private DynamicMessage toDynamicMessage(com.google.protobuf.Descriptors.Descriptor descriptor, String body) {
        try {
            DynamicMessage.Builder builder = DynamicMessage.newBuilder(descriptor);
            JsonFormat.parser().ignoringUnknownFields().merge(body == null || body.isBlank() ? "{}" : body, builder);
            return builder.build();
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "GRPC_REQUEST_JSON_INVALID",
                    "gRPC JSON request body 格式錯誤。",
                    java.util.Map.of("reason", exception.getMessage())
            );
        }
    }

    private List<HttpNameValue> trailersToHeaders(Metadata metadata) {
        if (metadata == null) {
            return List.of();
        }
        List<HttpNameValue> result = new ArrayList<>();
        for (String key : metadata.keys()) {
            if (key.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                result.add(new HttpNameValue(key, "<binary>", true));
            } else {
                Metadata.Key<String> metadataKey = Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER);
                result.add(new HttpNameValue(key, metadata.get(metadataKey), true));
            }
        }
        return result;
    }

    private String requiredText(String value, String code, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
        }
        return value.trim();
    }

    private int normalizePort(Integer port) {
        if (port == null || port <= 0 || port > 65535) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GRPC_PORT_INVALID", "Port 必須介於 1 到 65535。");
        }
        return port;
    }

    private int normalizeTimeout(Integer timeoutMillis) {
        int timeout = timeoutMillis == null ? DEFAULT_TIMEOUT_MILLIS : timeoutMillis;
        if (timeout <= 0 || timeout > MAX_TIMEOUT_MILLIS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GRPC_TIMEOUT_INVALID", "Timeout 必須介於 1 到 300000 毫秒。");
        }
        return timeout;
    }
}
