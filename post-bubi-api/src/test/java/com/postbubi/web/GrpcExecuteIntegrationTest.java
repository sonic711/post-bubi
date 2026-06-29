package com.postbubi.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:postbubi-grpc-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class GrpcExecuteIntegrationTest {

    private static final String SERVICE_NAME = "demo.EchoService";
    private static final String METHOD_NAME = "Echo";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private Server grpcServer;

    @AfterEach
    void stopGrpcServer() {
        if (grpcServer != null) {
            grpcServer.shutdownNow();
        }
    }

    @Test
    void executesUnaryGrpcMethodThroughServerReflection() throws Exception {
        Descriptors.FileDescriptor fileDescriptor = echoFileDescriptor();
        Descriptors.ServiceDescriptor serviceDescriptor = fileDescriptor.findServiceByName("EchoService");
        Descriptors.MethodDescriptor echoMethod = serviceDescriptor.findMethodByName(METHOD_NAME);
        grpcServer = NettyServerBuilder.forPort(0)
                .addService(echoService(fileDescriptor, echoMethod))
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start();

        ResponseEntity<String> response = postJson("/api/grpc/execute", """
                {
                  "host": "127.0.0.1",
                  "port": %d,
                  "plaintext": true,
                  "serviceName": "%s",
                  "methodName": "%s",
                  "body": "{\\"text\\":\\"hello\\"}",
                  "timeoutMillis": 30000
                }
                """.formatted(grpcServer.getPort(), SERVICE_NAME, METHOD_NAME));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode payload = objectMapper.readTree(response.getBody());
        assertThat(payload.path("statusCode").asText()).isEqualTo("OK");
        assertThat(payload.path("statusDescription").asText()).isEmpty();
        assertThat(payload.path("errorMessage").asText()).isEmpty();
        assertThat(payload.path("body").asText()).contains("\"text\": \"echo:hello\"");
    }

    @Test
    void returnsStructuredErrorForInvalidGrpcJsonBody() throws Exception {
        Descriptors.FileDescriptor fileDescriptor = echoFileDescriptor();
        Descriptors.ServiceDescriptor serviceDescriptor = fileDescriptor.findServiceByName("EchoService");
        Descriptors.MethodDescriptor echoMethod = serviceDescriptor.findMethodByName(METHOD_NAME);
        grpcServer = NettyServerBuilder.forPort(0)
                .addService(echoService(fileDescriptor, echoMethod))
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start();

        ResponseEntity<String> response = postJson("/api/grpc/execute", """
                {
                  "host": "127.0.0.1",
                  "port": %d,
                  "plaintext": true,
                  "serviceName": "%s",
                  "methodName": "%s",
                  "body": "{not-json}",
                  "timeoutMillis": 30000
                }
                """.formatted(grpcServer.getPort(), SERVICE_NAME, METHOD_NAME));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode error = objectMapper.readTree(response.getBody());
        assertThat(error.path("code").asText()).isEqualTo("GRPC_REQUEST_JSON_INVALID");
        assertThat(error.path("message").asText()).isEqualTo("gRPC JSON request body 格式錯誤。");
        assertThat(error.path("details").has("reason")).isTrue();
    }

    private ServerServiceDefinition echoService(
            Descriptors.FileDescriptor fileDescriptor,
            Descriptors.MethodDescriptor echoMethod
    ) {
        MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethod = MethodDescriptor
                .<DynamicMessage, DynamicMessage>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, METHOD_NAME))
                .setRequestMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(echoMethod.getInputType())))
                .setResponseMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(echoMethod.getOutputType())))
                .build();
        ServiceDescriptor grpcService = ServiceDescriptor.newBuilder(SERVICE_NAME)
                .setSchemaDescriptor((ProtoFileDescriptorSupplier) () -> fileDescriptor)
                .addMethod(grpcMethod)
                .build();
        Descriptors.FieldDescriptor requestText = echoMethod.getInputType().findFieldByName("text");
        Descriptors.FieldDescriptor responseText = echoMethod.getOutputType().findFieldByName("text");
        return ServerServiceDefinition.builder(grpcService)
                .addMethod(grpcMethod, ServerCalls.asyncUnaryCall((request, observer) -> {
                    String text = String.valueOf(request.getField(requestText));
                    DynamicMessage response = DynamicMessage.newBuilder(echoMethod.getOutputType())
                            .setField(responseText, "echo:" + text)
                            .build();
                    observer.onNext(response);
                    observer.onCompleted();
                }))
                .build();
    }

    private Descriptors.FileDescriptor echoFileDescriptor() throws Descriptors.DescriptorValidationException {
        DescriptorProtos.DescriptorProto echoRequest = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("EchoRequest")
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("text")
                        .setNumber(1)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                        .build())
                .build();
        DescriptorProtos.DescriptorProto echoResponse = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("EchoResponse")
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("text")
                        .setNumber(1)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                        .build())
                .build();
        DescriptorProtos.MethodDescriptorProto method = DescriptorProtos.MethodDescriptorProto.newBuilder()
                .setName(METHOD_NAME)
                .setInputType(".demo.EchoRequest")
                .setOutputType(".demo.EchoResponse")
                .build();
        DescriptorProtos.ServiceDescriptorProto service = DescriptorProtos.ServiceDescriptorProto.newBuilder()
                .setName("EchoService")
                .addMethod(method)
                .build();
        DescriptorProtos.FileDescriptorProto file = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("demo/echo.proto")
                .setSyntax("proto3")
                .setPackage("demo")
                .addMessageType(echoRequest)
                .addMessageType(echoResponse)
                .addService(service)
                .build();
        return Descriptors.FileDescriptor.buildFrom(file, new Descriptors.FileDescriptor[0]);
    }

    private ResponseEntity<String> postJson(String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }
}
