package com.postbubi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;

import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.ProtoFileDescriptorSupplier;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.stub.ServerCalls;

class HeadlessGrpcCliIntegrationTest {

    private static final String SERVICE_NAME = "demo.EchoService";
    private static final String METHOD_NAME = "Echo";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Server grpcServer;

    @AfterEach
    void stopServer() {
        if (grpcServer != null) grpcServer.shutdownNow();
    }

    @Test
    void executesExportedGrpcRequestWithEnvironmentVariablesWithoutStartingWebServer() throws Exception {
        Descriptors.FileDescriptor descriptor = echoFileDescriptor();
        Descriptors.MethodDescriptor method = descriptor.findServiceByName("EchoService").findMethodByName(METHOD_NAME);
        grpcServer = NettyServerBuilder.forPort(0)
                .addService(echoService(descriptor, method))
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start();

        Path archive = Files.createTempFile("post-bubi-cli-test-", ".zip");
        Path output = Files.createTempFile("post-bubi-cli-output-", ".json");
        try {
            writeArchive(archive, grpcServer.getPort(), false);
            int exitCode = new HeadlessGrpcCli().run(new String[] {
                    "run-grpc", "--archive", archive.toString(), "--request", "CLI Echo", "--environment", "sit", "--output", output.toString()
            });

            assertThat(exitCode).isZero();
            assertThat(objectMapper.readTree(Files.readString(output)).path("statusCode").asText()).isEqualTo("OK");
            assertThat(objectMapper.readTree(Files.readString(output)).path("body").asText()).contains("echo:from-environment");
        } finally {
            Files.deleteIfExists(archive);
            Files.deleteIfExists(output);
        }
    }

    @Test
    void executesExportedGrpcRequestWithArchiveProtoWhenReflectionIsDisabled() throws Exception {
        Descriptors.FileDescriptor descriptor = echoFileDescriptor();
        Descriptors.MethodDescriptor method = descriptor.findServiceByName("EchoService").findMethodByName(METHOD_NAME);
        grpcServer = NettyServerBuilder.forPort(0).addService(echoService(descriptor, method)).build().start();

        Path archive = Files.createTempFile("post-bubi-cli-proto-test-", ".zip");
        Path output = Files.createTempFile("post-bubi-cli-proto-output-", ".json");
        try {
            writeArchive(archive, grpcServer.getPort(), true);
            int exitCode = new HeadlessGrpcCli().run(new String[] {
                    "run-grpc", "--archive", archive.toString(), "--request", "CLI Echo", "--environment", "sit", "--output", output.toString()
            });

            assertThat(exitCode).isZero();
            assertThat(objectMapper.readTree(Files.readString(output)).path("body").asText()).contains("echo:from-environment");
        } finally {
            Files.deleteIfExists(archive);
            Files.deleteIfExists(output);
        }
    }

    @Test
    void executesSingleRequestArchiveWithoutEnvironment() throws Exception {
        Descriptors.FileDescriptor descriptor = echoFileDescriptor();
        Descriptors.MethodDescriptor method = descriptor.findServiceByName("EchoService").findMethodByName(METHOD_NAME);
        grpcServer = NettyServerBuilder.forPort(0)
                .addService(echoService(descriptor, method))
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start();

        Path archive = Files.createTempFile("post-bubi-cli-request-test-", ".zip");
        Path output = Files.createTempFile("post-bubi-cli-request-output-", ".json");
        try {
            writeArchive(archive, grpcServer.getPort(), false, "REQUEST", false);
            int exitCode = new HeadlessGrpcCli().run(new String[] {
                    "run-grpc", "--archive", archive.toString(), "--request", "CLI Echo", "--output", output.toString()
            });

            assertThat(exitCode).isZero();
            assertThat(objectMapper.readTree(Files.readString(output)).path("body").asText()).contains("echo:request-archive");
        } finally {
            Files.deleteIfExists(archive);
            Files.deleteIfExists(output);
        }
    }

    private void writeArchive(Path path, int port, boolean includeProto) throws Exception {
        writeArchive(path, port, includeProto, "COLLECTION", true);
    }

    private void writeArchive(Path path, int port, boolean includeProto, String archiveType, boolean includeEnvironment) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", 3);
        root.put("archiveType", archiveType);
        ArrayNode collections = root.putArray("collections");
        collections.addObject().put("id", 1).put("name", "CLI Tests");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("requestType", "GRPC");
        payload.put("grpcHost", includeEnvironment ? "{{host}}" : "127.0.0.1");
        payload.put("grpcPort", includeEnvironment ? "{{port}}" : String.valueOf(port));
        payload.put("grpcPlaintext", true);
        if (includeProto) payload.put("grpcProtoId", "cli-echo");
        payload.put("grpcServiceName", SERVICE_NAME);
        payload.put("grpcMethodName", METHOD_NAME);
        payload.put("grpcBody", includeEnvironment ? "{\"text\":\"{{message}}\"}" : "{\"text\":\"request-archive\"}");
        payload.put("timeoutMillis", 30000);
        ArrayNode requests = root.putArray("requests");
        requests.addObject().put("id", 1).put("collectionId", 1).put("type", "GRPC").put("name", "CLI Echo")
                .put("payloadJson", objectMapper.writeValueAsString(payload));
        if (includeEnvironment) {
            ArrayNode environments = root.putArray("environments");
            ArrayNode variables = environments.addObject().put("name", "sit").putArray("variables");
            variables.addObject().put("key", "host").put("value", "127.0.0.1");
            variables.addObject().put("key", "port").put("value", String.valueOf(port));
            variables.addObject().put("key", "message").put("value", "from-environment");
        } else {
            root.putArray("environments");
        }
        if (includeProto) root.putArray("protos").addObject()
                .put("protoId", "cli-echo")
                .put("path", "protos/cli-echo-echo.proto")
                .put("originalFilename", "echo.proto");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("collection.json"));
            output.write(objectMapper.writeValueAsBytes(root));
            output.closeEntry();
            if (includeProto) {
                output.putNextEntry(new ZipEntry("protos/cli-echo-echo.proto"));
                output.write(echoProtoSource().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }

    private ServerServiceDefinition echoService(Descriptors.FileDescriptor descriptor, Descriptors.MethodDescriptor method) {
        MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethod = MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, METHOD_NAME))
                .setRequestMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(method.getInputType())))
                .setResponseMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(method.getOutputType())))
                .build();
        ServiceDescriptor service = ServiceDescriptor.newBuilder(SERVICE_NAME)
                .setSchemaDescriptor((ProtoFileDescriptorSupplier) () -> descriptor)
                .addMethod(grpcMethod)
                .build();
        Descriptors.FieldDescriptor requestText = method.getInputType().findFieldByName("text");
        Descriptors.FieldDescriptor responseText = method.getOutputType().findFieldByName("text");
        return ServerServiceDefinition.builder(service)
                .addMethod(grpcMethod, ServerCalls.asyncUnaryCall((request, observer) -> {
                    DynamicMessage response = DynamicMessage.newBuilder(method.getOutputType())
                            .setField(responseText, "echo:" + request.getField(requestText))
                            .build();
                    observer.onNext(response);
                    observer.onCompleted();
                }))
                .build();
    }

    private Descriptors.FileDescriptor echoFileDescriptor() throws Exception {
        DescriptorProtos.DescriptorProto request = DescriptorProtos.DescriptorProto.newBuilder().setName("EchoRequest")
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder().setName("text").setNumber(1)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING).build())
                .build();
        DescriptorProtos.DescriptorProto response = DescriptorProtos.DescriptorProto.newBuilder().setName("EchoResponse")
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder().setName("text").setNumber(1)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING).build())
                .build();
        DescriptorProtos.FileDescriptorProto file = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("demo/echo.proto").setSyntax("proto3").setPackage("demo")
                .addMessageType(request).addMessageType(response)
                .addService(DescriptorProtos.ServiceDescriptorProto.newBuilder().setName("EchoService")
                        .addMethod(DescriptorProtos.MethodDescriptorProto.newBuilder().setName(METHOD_NAME)
                                .setInputType(".demo.EchoRequest").setOutputType(".demo.EchoResponse").build()).build())
                .build();
        return Descriptors.FileDescriptor.buildFrom(file, new Descriptors.FileDescriptor[0]);
    }

    private String echoProtoSource() {
        return """
                syntax = "proto3";
                package demo;
                message EchoRequest { string text = 1; }
                message EchoResponse { string text = 1; }
                service EchoService {
                  rpc Echo(EchoRequest) returns (EchoResponse);
                }
                """;
    }
}
