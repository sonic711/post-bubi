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
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.postbubi.execution.ExecutionCancellationService.ExecutionHandle;
import com.postbubi.web.dto.GrpcExecuteRequest;
import com.postbubi.web.dto.GrpcExecuteResponse;
import com.postbubi.web.dto.HttpNameValue;
import com.postbubi.web.error.ApiException;

@Service
public class GrpcExecuteService {

    private static final int DEFAULT_TIMEOUT_MILLIS = 30000;
    private static final int MAX_TIMEOUT_MILLIS = 300000;

    private final GrpcReflectionDescriptorResolver descriptorResolver;
    private final GrpcProtoDescriptorResolver protoDescriptorResolver;

    public GrpcExecuteService(
            GrpcReflectionDescriptorResolver descriptorResolver,
            GrpcProtoDescriptorResolver protoDescriptorResolver
    ) {
        this.descriptorResolver = descriptorResolver;
        this.protoDescriptorResolver = protoDescriptorResolver;
    }

    public GrpcExecuteResponse execute(GrpcExecuteRequest request, ExecutionHandle execution) {
        String host = requiredText(request.host(), "GRPC_HOST_REQUIRED", "Host 不可空白。");
        int port = normalizePort(request.port());
        int timeoutMillis = normalizeTimeout(request.timeoutMillis());
        String serviceName = requiredText(request.serviceName(), "GRPC_SERVICE_REQUIRED", "Service name 不可空白。");
        String methodName = requiredText(request.methodName(), "GRPC_METHOD_REQUIRED", "Method name 不可空白。");

        ManagedChannel channel = createChannel(host, port, request);
        long startNanos = System.nanoTime();
        try {
            execution.registerCancellationAction(channel::shutdownNow);
            if (execution.isCancelled()) {
                return cancelledResponse(startNanos);
            }
            var grpcMethod = resolveMethod(channel, request, serviceName, methodName);
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
            if (execution.isCancelled()) {
                return cancelledResponse(startNanos);
            }
            throw exception;
        } catch (StatusRuntimeException exception) {
            if (execution.isCancelled()) {
                return cancelledResponse(startNanos);
            }
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
            if (execution.isCancelled()) {
                return cancelledResponse(startNanos);
            }
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "GRPC_EXECUTE_FAILED",
                    "gRPC 請求執行失敗。",
                    java.util.Map.of("reason", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage())
            );
        } finally {
            execution.clearCancellationAction();
            channel.shutdownNow();
        }
    }

    private GrpcExecuteResponse cancelledResponse(long startNanos) {
        long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
        return new GrpcExecuteResponse(
                Status.Code.CANCELLED.name(),
                "請求已由使用者取消。",
                durationMillis,
                List.of(),
                "",
                "gRPC 請求已取消。"
        );
    }

    private com.google.protobuf.Descriptors.MethodDescriptor resolveMethod(
            ManagedChannel channel,
            GrpcExecuteRequest request,
            String serviceName,
            String methodName
    ) {
        if (request.protoId() != null && !request.protoId().isBlank()) {
            return protoDescriptorResolver.resolveMethod(request.protoId().trim(), serviceName, methodName);
        }
        return descriptorResolver.resolveMethod(channel, serviceName, methodName);
    }

    private ManagedChannel createChannel(String host, int port, GrpcExecuteRequest request) {
        if (Boolean.TRUE.equals(request.plaintext())) {
            return ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .build();
        }
        if (Boolean.TRUE.equals(request.ignoreTlsVerification())) {
            try {
                return NettyChannelBuilder.forAddress(host, port)
                        .sslContext(GrpcSslContexts.forClient()
                                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                .build())
                        .build();
            } catch (Exception exception) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "GRPC_TLS_CONFIG_INVALID",
                        "gRPC TLS 設定錯誤。",
                        java.util.Map.of("reason", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage())
                );
            }
        }
        return ManagedChannelBuilder.forAddress(host, port).build();
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
