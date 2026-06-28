package com.postbubi.grpc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.reflection.v1alpha.ServerReflectionGrpc;
import io.grpc.reflection.v1alpha.ServerReflectionRequest;
import io.grpc.reflection.v1alpha.ServerReflectionResponse;
import io.grpc.stub.StreamObserver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.postbubi.web.error.ApiException;

@Service
public class GrpcReflectionDescriptorResolver {

    public Descriptors.MethodDescriptor resolveMethod(
            ManagedChannel channel,
            String serviceName,
            String methodName
    ) {
        ServerReflectionGrpc.ServerReflectionStub stub = ServerReflectionGrpc.newStub(channel);
        ServerReflectionRequest request = ServerReflectionRequest.newBuilder()
                .setFileContainingSymbol(serviceName)
                .build();

        List<byte[]> descriptorBytes;
        try {
            ServerReflectionResponse response = reflect(stub, request);
            if (response == null) {
                throw badRequest("GRPC_REFLECTION_EMPTY", "gRPC reflection 沒有回傳 descriptor。");
            }
            if (response.hasErrorResponse()) {
                throw badRequest("GRPC_REFLECTION_FAILED", "gRPC reflection 查詢失敗：" + response.getErrorResponse().getErrorMessage());
            }
            descriptorBytes = response.getFileDescriptorResponse().getFileDescriptorProtoList().stream()
                    .map(ByteString::toByteArray)
                    .toList();
        } catch (StatusRuntimeException exception) {
            throw badRequest("GRPC_REFLECTION_FAILED", "gRPC reflection 查詢失敗：" + exception.getStatus().getDescription());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw badRequest("GRPC_REFLECTION_INTERRUPTED", "gRPC reflection 查詢被中斷。");
        }

        Map<String, FileDescriptorProto> protos = new HashMap<>();
        for (byte[] bytes : descriptorBytes) {
            try {
                FileDescriptorProto proto = FileDescriptorProto.parseFrom(bytes);
                protos.put(proto.getName(), proto);
            } catch (Exception exception) {
                throw badRequest("GRPC_DESCRIPTOR_INVALID", "gRPC descriptor 解析失敗。");
            }
        }

        Map<String, Descriptors.FileDescriptor> built = new HashMap<>();
        for (String name : protos.keySet()) {
            buildFileDescriptor(name, protos, built);
        }

        for (Descriptors.FileDescriptor descriptor : built.values()) {
            Descriptors.ServiceDescriptor service = descriptor.findServiceByName(shortName(serviceName));
            if (service != null && service.getFullName().equals(serviceName)) {
                Descriptors.MethodDescriptor method = service.findMethodByName(methodName);
                if (method == null) {
                    throw badRequest("GRPC_METHOD_NOT_FOUND", "找不到指定的 gRPC method。");
                }
                if (method.isClientStreaming() || method.isServerStreaming()) {
                    throw badRequest("GRPC_METHOD_NOT_UNARY", "目前只支援 unary gRPC method。");
                }
                return method;
            }
        }

        throw badRequest("GRPC_SERVICE_NOT_FOUND", "找不到指定的 gRPC service。");
    }

    private ServerReflectionResponse reflect(
            ServerReflectionGrpc.ServerReflectionStub stub,
            ServerReflectionRequest request
    ) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ServerReflectionResponse> responseRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        StreamObserver<ServerReflectionRequest> requestObserver = stub.serverReflectionInfo(new StreamObserver<>() {
            @Override
            public void onNext(ServerReflectionResponse response) {
                responseRef.compareAndSet(null, response);
                latch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                errorRef.set(throwable);
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        });
        requestObserver.onNext(request);
        requestObserver.onCompleted();
        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw badRequest("GRPC_REFLECTION_TIMEOUT", "gRPC reflection 查詢逾時。");
        }
        Throwable error = errorRef.get();
        if (error instanceof StatusRuntimeException statusRuntimeException) {
            throw statusRuntimeException;
        }
        if (error != null) {
            throw badRequest("GRPC_REFLECTION_FAILED", "gRPC reflection 查詢失敗：" + error.getMessage());
        }
        return responseRef.get();
    }

    private Descriptors.FileDescriptor buildFileDescriptor(
            String name,
            Map<String, FileDescriptorProto> protos,
            Map<String, Descriptors.FileDescriptor> built
    ) {
        if (built.containsKey(name)) {
            return built.get(name);
        }
        FileDescriptorProto proto = protos.get(name);
        if (proto == null) {
            throw badRequest("GRPC_DESCRIPTOR_DEPENDENCY_MISSING", "gRPC descriptor 缺少相依 proto：" + name);
        }
        Descriptors.FileDescriptor[] dependencies = proto.getDependencyList().stream()
                .map(dependency -> buildFileDescriptor(dependency, protos, built))
                .toArray(Descriptors.FileDescriptor[]::new);
        try {
            Descriptors.FileDescriptor descriptor = Descriptors.FileDescriptor.buildFrom(proto, dependencies);
            built.put(name, descriptor);
            return descriptor;
        } catch (Descriptors.DescriptorValidationException exception) {
            throw badRequest("GRPC_DESCRIPTOR_INVALID", "gRPC descriptor 驗證失敗：" + exception.getMessage());
        }
    }

    private String shortName(String fullName) {
        int index = fullName.lastIndexOf('.');
        return index < 0 ? fullName : fullName.substring(index + 1);
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
