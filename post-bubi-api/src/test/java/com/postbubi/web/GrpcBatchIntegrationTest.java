package com.postbubi.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
                "spring.datasource.url=jdbc:h2:mem:postbubi-grpc-batch-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "post-bubi.bur.code-table-dir=./build/test-files/missing-code-table"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class GrpcBatchIntegrationTest {

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
    void executesUnaryGrpcBatchAndPersistsProtocolSpecificHistory() throws Exception {
        Descriptors.FileDescriptor descriptor = echoFileDescriptor();
        Descriptors.MethodDescriptor echoMethod = descriptor.findServiceByName("EchoService").findMethodByName(METHOD_NAME);
        grpcServer = NettyServerBuilder.forPort(0)
                .addService(echoService(descriptor, echoMethod))
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start();

        ResponseEntity<String> start = postJson("/api/grpc/batch-runs", """
                {
                  "requestId": 701,
                  "grpcRequest": {
                    "host": "127.0.0.1",
                    "port": %d,
                    "plaintext": true,
                    "serviceName": "%s",
                    "methodName": "%s",
                    "body": "{\\"text\\":\\"batch\\"}",
                    "timeoutMillis": 30000
                  },
                  "mode": "CONCURRENCY",
                  "totalCount": 3,
                  "maxConcurrency": 2
                }
                """.formatted(grpcServer.getPort(), SERVICE_NAME, METHOD_NAME));

        assertThat(start.getStatusCode()).isEqualTo(HttpStatus.OK);
        long runId = objectMapper.readTree(start.getBody()).path("id").asLong();
        JsonNode completed = awaitTerminal("/api/grpc/batch-runs/", runId);
        assertThat(completed.path("protocol").asText()).isEqualTo("GRPC");
        assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.path("successCount").asInt()).isEqualTo(3);

        JsonNode items = objectMapper.readTree(restTemplate.getForEntity(
                "/api/grpc/batch-runs/" + runId + "/items?page=0&size=100", String.class).getBody());
        assertThat(items.path("items")).hasSize(3);
        assertThat(items.path("items").get(0).path("statusCode").asText()).isEqualTo("OK");
        assertThat(items.path("items").get(0).path("responseBodyPreview").asText()).contains("echo:batch");
        assertThat(items.path("items").get(0).path("startedAt").asText()).isNotBlank();
        assertThat(items.path("items").get(0).path("completedAt").asText()).isNotBlank();

        JsonNode grpcHistory = objectMapper.readTree(restTemplate.getForEntity(
                "/api/grpc/batch-runs?requestId=701", String.class).getBody());
        JsonNode burHistory = objectMapper.readTree(restTemplate.getForEntity(
                "/api/grpc-bur/batch-runs?requestId=701", String.class).getBody());
        assertThat(grpcHistory.path("totalItems").asInt()).isEqualTo(1);
        assertThat(burHistory.path("totalItems").asInt()).isZero();

        ResponseEntity<byte[]> csv = restTemplate.getForEntity("/api/grpc/batch-runs/" + runId + "/export.csv", byte[].class);
        assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(csv.getBody(), StandardCharsets.UTF_8)).contains("itemStartedAt,itemCompletedAt", "+08:00", "GRPC", "echo:batch");

        ResponseEntity<String> clear = restTemplate.exchange(
                "/api/grpc/batch-runs?requestId=701", HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
        assertThat(clear.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(clear.getBody()).path("deletedCount").asLong()).isEqualTo(1);
        JsonNode clearedHistory = objectMapper.readTree(restTemplate.getForEntity(
                "/api/grpc/batch-runs?requestId=701", String.class).getBody());
        assertThat(clearedHistory.path("totalItems").asInt()).isZero();
    }

    @Test
    void appliesPayloadDataBase64EncodingToEveryUnaryGrpcBatchItem() throws Exception {
        Descriptors.FileDescriptor descriptor = echoFileDescriptor();
        Descriptors.MethodDescriptor echoMethod = descriptor.findServiceByName("EchoService").findMethodByName(METHOD_NAME);
        grpcServer = NettyServerBuilder.forPort(0)
                .addService(echoService(descriptor, echoMethod))
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start();

        String plainText = "批次測試";
        String encoded = Base64.getEncoder().encodeToString(plainText.getBytes(StandardCharsets.UTF_8));
        ResponseEntity<String> start = postJson("/api/grpc/batch-runs", """
                {
                  "grpcRequest": {
                    "host": "127.0.0.1",
                    "port": %d,
                    "plaintext": true,
                    "serviceName": "%s",
                    "methodName": "%s",
                    "body": "{\\"payload\\":{\\"data\\":\\"%s\\"}}",
                    "encodePayloadDataBase64": true,
                    "timeoutMillis": 30000
                  },
                  "mode": "CONCURRENCY",
                  "totalCount": 2,
                  "maxConcurrency": 1
                }
                """.formatted(grpcServer.getPort(), SERVICE_NAME, METHOD_NAME, plainText));

        assertThat(start.getStatusCode()).isEqualTo(HttpStatus.OK);
        long runId = runId(start);
        assertThat(awaitTerminal("/api/grpc/batch-runs/", runId).path("successCount").asInt()).isEqualTo(2);
        JsonNode items = objectMapper.readTree(restTemplate.getForEntity(
                "/api/grpc/batch-runs/" + runId + "/items?page=0&size=100", String.class).getBody());
        assertThat(items.path("items")).allSatisfy(item ->
                assertThat(item.path("responseBodyPreview").asText()).contains("echo:" + encoded));
    }

    @Test
    void executesResponseIntervalDeadlineAndManualCancellationForUnaryGrpcBatch() throws Exception {
        Descriptors.FileDescriptor descriptor = echoFileDescriptor();
        Descriptors.MethodDescriptor echoMethod = descriptor.findServiceByName("EchoService").findMethodByName(METHOD_NAME);
        List<Instant> starts = new CopyOnWriteArrayList<>();
        grpcServer = NettyServerBuilder.forPort(0)
                .addService(echoService(descriptor, echoMethod, () -> starts.add(Instant.now())))
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start();

        long intervalRunId = runId(startGrpcBatch("RESPONSE_INTERVAL", 3, null, 120, null));
        JsonNode intervalRun = awaitTerminal("/api/grpc/batch-runs/", intervalRunId);
        assertThat(intervalRun.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(starts).hasSize(3);
        assertThat(Duration.between(starts.get(0), starts.get(1)).toMillis()).isGreaterThanOrEqualTo(100);
        assertThat(Duration.between(starts.get(1), starts.get(2)).toMillis()).isGreaterThanOrEqualTo(100);

        grpcServer.shutdownNow();
        CountDownLatch invocationStarted = new CountDownLatch(1);
        grpcServer = NettyServerBuilder.forPort(0)
                .addService(waitingEchoService(descriptor, echoMethod, invocationStarted))
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start();

        long deadlineRunId = runId(startGrpcBatch("DEADLINE", 3, 1, null, 1000));
        assertThat(invocationStarted.await(3, TimeUnit.SECONDS)).isTrue();
        JsonNode deadlineRun = awaitTerminal("/api/grpc/batch-runs/", deadlineRunId);
        assertThat(deadlineRun.path("status").asText()).isEqualTo("DEADLINE_EXCEEDED");
        assertThat(deadlineRun.path("notDispatchedCount").asInt()).isGreaterThanOrEqualTo(2);

        CountDownLatch cancelledInvocationStarted = new CountDownLatch(1);
        grpcServer.shutdownNow();
        grpcServer = NettyServerBuilder.forPort(0)
                .addService(waitingEchoService(descriptor, echoMethod, cancelledInvocationStarted))
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start();
        long cancelRunId = runId(startGrpcBatch("CONCURRENCY", 3, 1, null, null));
        assertThat(cancelledInvocationStarted.await(3, TimeUnit.SECONDS)).isTrue();
        ResponseEntity<String> cancel = postJson("/api/grpc/batch-runs/" + cancelRunId + "/cancel", "");
        assertThat(cancel.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode cancelledRun = awaitTerminal("/api/grpc/batch-runs/", cancelRunId);
        assertThat(cancelledRun.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelledRun.path("notDispatchedCount").asInt()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void recordsGrpcBurBatchFailuresWithResponseIntervalAndAllowsCancellation() throws Exception {
        ResponseEntity<String> start = postJson("/api/grpc-bur/batch-runs", """
                {
                  "requestId": 702,
                  "grpcBurRequest": {
                    "basicLabel": "TOO-LONG",
                    "settings": { "basicLabelLength": 2 }
                  },
                  "mode": "RESPONSE_INTERVAL",
                  "totalCount": 2,
                  "intervalMillis": 120
                }
                """);

        assertThat(start.getStatusCode()).isEqualTo(HttpStatus.OK);
        long runId = objectMapper.readTree(start.getBody()).path("id").asLong();
        JsonNode completed = awaitTerminal("/api/grpc-bur/batch-runs/", runId);
        assertThat(completed.path("protocol").asText()).isEqualTo("GRPC_BUR");
        assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.path("failedCount").asInt()).isEqualTo(2);
        assertThat(completed.path("durationMillis").asLong()).isGreaterThanOrEqualTo(100);

        JsonNode items = objectMapper.readTree(restTemplate.getForEntity(
                "/api/grpc-bur/batch-runs/" + runId + "/items", String.class).getBody());
        assertThat(items.path("items").get(0).path("status").asText()).isEqualTo("FAILED");
        assertThat(items.path("items").get(0).path("errorMessage").asText()).contains("Basic Label");
        assertThat(items.path("items").get(0).path("startedAt").asText()).isNotBlank();
        assertThat(items.path("items").get(0).path("completedAt").asText()).isNotBlank();
        ResponseEntity<byte[]> csv = restTemplate.getForEntity(
                "/api/grpc-bur/batch-runs/" + runId + "/export.csv", byte[].class);
        assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(csv.getBody(), StandardCharsets.UTF_8)).contains("itemStartedAt,itemCompletedAt", "+08:00", "GRPC_BUR");

        long cancellableRunId = runId(postJson("/api/grpc-bur/batch-runs", """
                {
                  "grpcBurRequest": {
                    "basicLabel": "TOO-LONG",
                    "settings": { "basicLabelLength": 2 }
                  },
                  "mode": "RESPONSE_INTERVAL",
                  "totalCount": 2,
                  "intervalMillis": 300000
                }
                """));
        waitForFirstItemCompletion("/api/grpc-bur/batch-runs/", cancellableRunId);
        ResponseEntity<String> cancel = postJson("/api/grpc-bur/batch-runs/" + cancellableRunId + "/cancel", "");
        assertThat(cancel.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode cancelledRun = awaitTerminal("/api/grpc-bur/batch-runs/", cancellableRunId);
        assertThat(cancelledRun.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelledRun.path("notDispatchedCount").asInt()).isEqualTo(1);
    }

    private ResponseEntity<String> startGrpcBatch(
            String mode,
            int totalCount,
            Integer maxConcurrency,
            Integer intervalMillis,
            Integer deadlineMillis
    ) {
        String concurrency = maxConcurrency == null ? "null" : maxConcurrency.toString();
        String interval = intervalMillis == null ? "null" : intervalMillis.toString();
        String deadline = deadlineMillis == null ? "null" : deadlineMillis.toString();
        return postJson("/api/grpc/batch-runs", """
                {
                  "grpcRequest": {
                    "host": "127.0.0.1",
                    "port": %d,
                    "plaintext": true,
                    "serviceName": "%s",
                    "methodName": "%s",
                    "body": "{\\"text\\":\\"batch\\"}",
                    "timeoutMillis": 30000
                  },
                  "mode": "%s",
                  "totalCount": %d,
                  "maxConcurrency": %s,
                  "intervalMillis": %s,
                  "deadlineMillis": %s
                }
                """.formatted(grpcServer.getPort(), SERVICE_NAME, METHOD_NAME, mode, totalCount, concurrency, interval, deadline));
    }

    private JsonNode awaitTerminal(String pathPrefix, long runId) throws Exception {
        for (int attempt = 0; attempt < 80; attempt++) {
            JsonNode payload = objectMapper.readTree(restTemplate.getForEntity(pathPrefix + runId, String.class).getBody());
            if (!"RUNNING".equals(payload.path("status").asText())) {
                return payload;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        throw new AssertionError("gRPC Batch did not finish in time");
    }

    private void waitForFirstItemCompletion(String pathPrefix, long runId) throws Exception {
        for (int attempt = 0; attempt < 80; attempt++) {
            JsonNode payload = objectMapper.readTree(restTemplate.getForEntity(pathPrefix + runId, String.class).getBody());
            if (payload.path("failedCount").asInt() == 1 && "RUNNING".equals(payload.path("status").asText())) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(25);
        }
        throw new AssertionError("gRPC BUR Batch did not enter its response interval");
    }

    private ServerServiceDefinition echoService(Descriptors.FileDescriptor descriptor, Descriptors.MethodDescriptor echoMethod) {
        return echoService(descriptor, echoMethod, () -> { });
    }

    private ServerServiceDefinition echoService(Descriptors.FileDescriptor descriptor, Descriptors.MethodDescriptor echoMethod, Runnable beforeResponse) {
        MethodDescriptor<DynamicMessage, DynamicMessage> method = MethodDescriptor
                .<DynamicMessage, DynamicMessage>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, METHOD_NAME))
                .setRequestMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(echoMethod.getInputType())))
                .setResponseMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(echoMethod.getOutputType())))
                .build();
        ServiceDescriptor service = ServiceDescriptor.newBuilder(SERVICE_NAME)
                .setSchemaDescriptor((ProtoFileDescriptorSupplier) () -> descriptor)
                .addMethod(method)
                .build();
        Descriptors.FieldDescriptor requestText = echoMethod.getInputType().findFieldByName("text");
        Descriptors.FieldDescriptor requestPayload = echoMethod.getInputType().findFieldByName("payload");
        Descriptors.FieldDescriptor payloadData = requestPayload.getMessageType().findFieldByName("data");
        Descriptors.FieldDescriptor responseText = echoMethod.getOutputType().findFieldByName("text");
        return ServerServiceDefinition.builder(service)
                .addMethod(method, ServerCalls.asyncUnaryCall((request, observer) -> {
                    beforeResponse.run();
                    String text = String.valueOf(request.getField(requestText));
                    if (text.isEmpty() && request.hasField(requestPayload)) {
                        DynamicMessage payload = (DynamicMessage) request.getField(requestPayload);
                        text = String.valueOf(payload.getField(payloadData));
                    }
                    DynamicMessage response = DynamicMessage.newBuilder(echoMethod.getOutputType())
                            .setField(responseText, "echo:" + text)
                            .build();
                    observer.onNext(response);
                    observer.onCompleted();
                }))
                .build();
    }

    private ServerServiceDefinition waitingEchoService(
            Descriptors.FileDescriptor descriptor,
            Descriptors.MethodDescriptor echoMethod,
            CountDownLatch invocationStarted
    ) {
        MethodDescriptor<DynamicMessage, DynamicMessage> method = MethodDescriptor
                .<DynamicMessage, DynamicMessage>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, METHOD_NAME))
                .setRequestMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(echoMethod.getInputType())))
                .setResponseMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(echoMethod.getOutputType())))
                .build();
        ServiceDescriptor service = ServiceDescriptor.newBuilder(SERVICE_NAME)
                .setSchemaDescriptor((ProtoFileDescriptorSupplier) () -> descriptor)
                .addMethod(method)
                .build();
        return ServerServiceDefinition.builder(service)
                .addMethod(method, ServerCalls.asyncUnaryCall((request, observer) -> invocationStarted.countDown()))
                .build();
    }

    private Descriptors.FileDescriptor echoFileDescriptor() throws Descriptors.DescriptorValidationException {
        DescriptorProtos.DescriptorProto payload = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("Payload")
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder().setName("data").setNumber(1)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .build();
        DescriptorProtos.DescriptorProto request = message("EchoRequest").toBuilder()
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder().setName("payload").setNumber(2)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE).setTypeName(".demo.Payload"))
                .build();
        DescriptorProtos.DescriptorProto response = message("EchoResponse");
        DescriptorProtos.FileDescriptorProto file = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("demo/echo.proto")
                .setSyntax("proto3")
                .setPackage("demo")
                .addMessageType(payload)
                .addMessageType(request)
                .addMessageType(response)
                .addService(DescriptorProtos.ServiceDescriptorProto.newBuilder().setName("EchoService")
                        .addMethod(DescriptorProtos.MethodDescriptorProto.newBuilder().setName(METHOD_NAME)
                                .setInputType(".demo.EchoRequest").setOutputType(".demo.EchoResponse")))
                .build();
        return Descriptors.FileDescriptor.buildFrom(file, new Descriptors.FileDescriptor[0]);
    }

    private DescriptorProtos.DescriptorProto message(String name) {
        return DescriptorProtos.DescriptorProto.newBuilder().setName(name)
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder().setName("text").setNumber(1)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .build();
    }

    private ResponseEntity<String> postJson(String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(path, new HttpEntity<>(body, headers), String.class);
    }

    private long runId(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody()).path("id").asLong();
    }
}
