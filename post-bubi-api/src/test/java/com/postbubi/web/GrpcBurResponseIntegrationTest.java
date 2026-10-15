package com.postbubi.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.postbubi.grpcbur.BurCodecService;

import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.ProtoFileDescriptorSupplier;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.stub.ServerCalls;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:postbubi-bur-response-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "post-bubi.bur.code-table-dir=./build/test-files/missing-code-table"
})
class GrpcBurResponseIntegrationTest {
    @Autowired private TestRestTemplate rest;
    @Autowired private ObjectMapper mapper;
    @Autowired private BurCodecService codec;

    @Test
    void decodesActualUnaryResponseAndPersistsBatchDecodedPayloads() throws Exception {
        var responseType = DescriptorProtos.DescriptorProto.newBuilder().setName("Response")
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder().setName("payload").setNumber(1)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE).setTypeName(".google.protobuf.Struct"));
        var file = Descriptors.FileDescriptor.buildFrom(DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("bur-response.proto").setPackage("testbur").setSyntax("proto3")
                .addDependency("google/protobuf/struct.proto")
                .addMessageType(DescriptorProtos.DescriptorProto.newBuilder().setName("Request"))
                .addMessageType(responseType)
                .addService(DescriptorProtos.ServiceDescriptorProto.newBuilder().setName("Service")
                        .addMethod(DescriptorProtos.MethodDescriptorProto.newBuilder().setName("Echo")
                                .setInputType(".testbur.Request").setOutputType(".testbur.Response")))
                .build(), new Descriptors.FileDescriptor[] { Struct.getDescriptor().getFile() });
        var method = file.findServiceByName("Service").findMethodByName("Echo");
        var grpcMethod = MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY).setFullMethodName("testbur.Service/Echo")
                .setRequestMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(method.getInputType())))
                .setResponseMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(method.getOutputType()))).build();
        String encoded = Base64.getEncoder().encodeToString(codec.encode("繁體測試"));
        Struct item = Struct.newBuilder().putFields("charsets", stringValue("BUR"))
                .putFields("format", stringValue("TEXT")).putFields("data", stringValue(encoded)).build();
        var reply = DynamicMessage.newBuilder(method.getOutputType())
                .setField(method.getOutputType().findFieldByName("payload"), Struct.newBuilder()
                        .putFields("0", Value.newBuilder().setStructValue(item).build()).build()).build();
        var service = ServiceDescriptor.newBuilder("testbur.Service")
                .setSchemaDescriptor((ProtoFileDescriptorSupplier) () -> file).addMethod(grpcMethod).build();
        var server = NettyServerBuilder.forPort(0).addService(ServerServiceDefinition.builder(service)
                .addMethod(grpcMethod, ServerCalls.asyncUnaryCall((request, observer) -> {
                    observer.onNext(reply);
                    observer.onCompleted();
                })).build()).addService(ProtoReflectionService.newInstance()).build().start();
        try {
            var request = mapper.createObjectNode().put("host", "127.0.0.1").put("port", server.getPort())
                    .put("plaintext", true).put("serviceName", "testbur.Service").put("methodName", "Echo").put("body", "{}");
            JsonNode single = post("/api/grpc/execute", request);
            assertThat(single.path("statusCode").asText()).isEqualTo("OK");
            assertThat(mapper.readTree(single.path("body").asText()).path("payload").path("0").path("data").asText()).isEqualTo(encoded);
            assertThat(single.path("decodedPayloads").get(0).path("text").asText()).isEqualTo("繁體測試");
            var batch = mapper.createObjectNode().put("mode", "CONCURRENCY").put("totalCount", 2).put("maxConcurrency", 2);
            batch.set("grpcRequest", request);
            long id = post("/api/grpc/batch-runs", batch).path("id").asLong();
            JsonNode run = null;
            for (int attempt = 0; attempt < 100; attempt++) {
                run = mapper.readTree(rest.getForObject("/api/grpc/batch-runs/" + id, String.class));
                if (run.path("status").asText().equals("COMPLETED")) break;
                Thread.sleep(50);
            }
            assertThat(run.path("successCount").asInt()).isEqualTo(2);
            JsonNode items = mapper.readTree(rest.getForObject("/api/grpc/batch-runs/" + id + "/items", String.class)).path("items");
            assertThat(items).hasSize(2).allSatisfy(value ->
                    assertThat(value.path("decodedPayloads").get(0).path("text").asText()).isEqualTo("繁體測試"));
        } finally {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private JsonNode post(String path, JsonNode body) throws Exception {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var response = rest.postForEntity(path, new HttpEntity<>(body.toString(), headers), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).as(response.getBody()).isTrue();
        return mapper.readTree(response.getBody());
    }

    private Value stringValue(String value) { return Value.newBuilder().setStringValue(value).build(); }
}
