package com.postbubi.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
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
                "spring.datasource.url=jdbc:h2:mem:postbubi-http-batch-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class HttpBatchIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void executesConcurrentBatchAndKeepsSingleRequestHistoryClean() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        try (TargetServer target = TargetServer.start(exchange -> {
            requestCount.incrementAndGet();
            writeResponse(exchange, 200, "ok");
        })) {
            ResponseEntity<String> startResponse = startBatch(target.port(), "CONCURRENCY", 3, 2, null, null);
            assertThat(startResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode completed = awaitTerminal(runId(startResponse));
            assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
            assertThat(completed.path("successCount").asInt()).isEqualTo(3);
            assertThat(completed.path("failedCount").asInt()).isZero();
            assertThat(requestCount.get()).isEqualTo(3);

            JsonNode items = getItems(runId(startResponse));
            assertThat(items.path("totalItems").asInt()).isEqualTo(3);
            assertThat(items.path("items")).hasSize(3);
            assertThat(items.path("items").get(0).path("status").asText()).isEqualTo("SUCCESS");

            ResponseEntity<String> runs = restTemplate.getForEntity("/api/http/batch-runs?size=20", String.class);
            JsonNode runPage = objectMapper.readTree(runs.getBody());
            assertThat(runPage.path("totalItems").asInt()).isEqualTo(1);
            assertThat(runPage.path("items").get(0).path("id").asLong()).isEqualTo(runId(startResponse));

            ResponseEntity<String> history = restTemplate.getForEntity("/api/http/history", String.class);
            assertThat(objectMapper.readTree(history.getBody())).isEmpty();
        }
    }

    @Test
    void acceptsBatchAboveFormerHundredRequestLimitAndPagesItemResults() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        try (TargetServer target = TargetServer.start(exchange -> {
            requestCount.incrementAndGet();
            writeResponse(exchange, 200, "ok");
        })) {
            ResponseEntity<String> startResponse = startBatch(target.port(), "CONCURRENCY", 101, 10, null, null);
            assertThat(startResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(objectMapper.readTree(startResponse.getBody()).path("totalCount").asInt()).isEqualTo(101);

            JsonNode completed = awaitTerminal(runId(startResponse));
            assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
            assertThat(completed.path("successCount").asInt()).isEqualTo(101);
            assertThat(completed.path("failedCount").asInt()).isZero();
            assertThat(requestCount.get()).isEqualTo(101);

            JsonNode firstPage = getItems(runId(startResponse), 0, 100);
            assertThat(firstPage.path("totalItems").asInt()).isEqualTo(101);
            assertThat(firstPage.path("items")).hasSize(100);
            assertThat(firstPage.path("items").get(0).path("sequenceNumber").asInt()).isEqualTo(1);
            assertThat(firstPage.path("items").get(99).path("sequenceNumber").asInt()).isEqualTo(100);

            JsonNode secondPage = getItems(runId(startResponse), 1, 100);
            assertThat(secondPage.path("totalItems").asInt()).isEqualTo(101);
            assertThat(secondPage.path("items")).hasSize(1);
            assertThat(secondPage.path("items").get(0).path("sequenceNumber").asInt()).isEqualTo(101);
        }
    }

    @Test
    void waitsForResponseThenConfiguredIntervalBeforeNextRequest() throws Exception {
        List<Instant> requestStarts = new CopyOnWriteArrayList<>();
        try (TargetServer target = TargetServer.start(exchange -> {
            requestStarts.add(Instant.now());
            writeResponse(exchange, 200, "ok");
        })) {
            ResponseEntity<String> startResponse = startBatch(target.port(), "RESPONSE_INTERVAL", 3, null, 120, null);
            JsonNode completed = awaitTerminal(runId(startResponse));

            assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
            assertThat(requestStarts).hasSize(3);
            assertThat(Duration.between(requestStarts.get(0), requestStarts.get(1)).toMillis()).isGreaterThanOrEqualTo(100);
            assertThat(Duration.between(requestStarts.get(1), requestStarts.get(2)).toMillis()).isGreaterThanOrEqualTo(100);
        }
    }

    @Test
    void cancelsUndispatchedRequestsWhenDeadlineExpires() throws Exception {
        CountDownLatch firstRequestStarted = new CountDownLatch(1);
        try (TargetServer target = TargetServer.start(exchange -> {
            firstRequestStarted.countDown();
            sleepForSlowResponse();
            writeResponse(exchange, 200, "late");
        })) {
            ResponseEntity<String> startResponse = startBatch(target.port(), "DEADLINE", 3, 1, null, 1000);
            assertThat(firstRequestStarted.await(3, TimeUnit.SECONDS)).isTrue();

            JsonNode completed = awaitTerminal(runId(startResponse));
            assertThat(completed.path("status").asText()).isEqualTo("DEADLINE_EXCEEDED");
            assertThat(completed.path("notDispatchedCount").asInt()).isGreaterThanOrEqualTo(2);
            assertThat(completed.path("cancelledCount").asInt()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void cancelsActiveBatchOnUserRequest() throws Exception {
        CountDownLatch firstRequestStarted = new CountDownLatch(1);
        try (TargetServer target = TargetServer.start(exchange -> {
            firstRequestStarted.countDown();
            sleepForSlowResponse();
            writeResponse(exchange, 200, "late");
        })) {
            ResponseEntity<String> startResponse = startBatch(target.port(), "CONCURRENCY", 3, 1, null, null);
            Long runId = runId(startResponse);
            assertThat(firstRequestStarted.await(3, TimeUnit.SECONDS)).isTrue();

            ResponseEntity<String> cancelResponse = postJson("/api/http/batch-runs/" + runId + "/cancel", "");
            assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode completed = awaitCancelledItem(runId);
            assertThat(completed.path("status").asText()).isEqualTo("CANCELLED");
            assertThat(completed.path("notDispatchedCount").asInt()).isGreaterThanOrEqualTo(2);
            assertThat(completed.path("cancelledCount").asInt()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void exportsBatchCsvAndClearsOnlyCompletedRunsForCurrentRequest() throws Exception {
        try (TargetServer target = TargetServer.start(exchange -> writeResponse(exchange, 200, "batch ok"))) {
            ResponseEntity<String> currentRequestRun = startBatch(target.port(), "CONCURRENCY", 2, 1, null, null, 501L);
            ResponseEntity<String> otherRequestRun = startBatch(target.port(), "CONCURRENCY", 1, 1, null, null, 502L);
            Long currentRunId = runId(currentRequestRun);
            awaitTerminal(currentRunId);
            awaitTerminal(runId(otherRequestRun));

            ResponseEntity<byte[]> exportResponse = restTemplate.getForEntity(
                    "/api/http/batch-runs/" + currentRunId + "/export.csv", byte[].class);
            assertThat(exportResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(exportResponse.getHeaders().getContentType().toString()).isEqualTo("text/csv;charset=UTF-8");
            assertThat(exportResponse.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                    .contains("post-bubi-batch-" + currentRunId + ".csv");
            String csv = new String(exportResponse.getBody(), StandardCharsets.UTF_8);
            assertThat(csv).startsWith("\uFEFFbatchRunId,requestId");
            assertThat(csv).contains("itemStartedAt,itemCompletedAt", "+08:00", "\"501\"", "\"SUCCESS\"", "\"batch ok\"");

            ResponseEntity<String> clearResponse = restTemplate.exchange(
                    "/api/http/batch-runs?requestId=501", HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
            assertThat(clearResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(objectMapper.readTree(clearResponse.getBody()).path("deletedCount").asLong()).isEqualTo(1);

            JsonNode currentHistory = objectMapper.readTree(restTemplate.getForEntity(
                    "/api/http/batch-runs?requestId=501", String.class).getBody());
            JsonNode otherHistory = objectMapper.readTree(restTemplate.getForEntity(
                    "/api/http/batch-runs?requestId=502", String.class).getBody());
            assertThat(currentHistory.path("totalItems").asInt()).isZero();
            assertThat(otherHistory.path("totalItems").asInt()).isEqualTo(1);
        }
    }

    private ResponseEntity<String> startBatch(
            int targetPort,
            String mode,
            int totalCount,
            Integer maxConcurrency,
            Integer intervalMillis,
            Integer deadlineMillis
    ) {
        return startBatch(targetPort, mode, totalCount, maxConcurrency, intervalMillis, deadlineMillis, null);
    }

    private ResponseEntity<String> startBatch(
            int targetPort,
            String mode,
            int totalCount,
            Integer maxConcurrency,
            Integer intervalMillis,
            Integer deadlineMillis,
            Long requestId
    ) {
        String maxConcurrencyJson = maxConcurrency == null ? "null" : maxConcurrency.toString();
        String intervalJson = intervalMillis == null ? "null" : intervalMillis.toString();
        String deadlineJson = deadlineMillis == null ? "null" : deadlineMillis.toString();
        String requestIdJson = requestId == null ? "" : "\"requestId\": " + requestId + ",";
        return postJson("/api/http/batch-runs", """
                {
                  "httpRequest": {
                    %s
                    "method": "GET",
                    "url": "http://127.0.0.1:%d/batch",
                    "bodyType": "none",
                    "timeoutMillis": 30000,
                    "followRedirects": true,
                    "ignoreSslVerification": false
                  },
                  "mode": "%s",
                  "totalCount": %d,
                  "maxConcurrency": %s,
                  "intervalMillis": %s,
                  "deadlineMillis": %s
                }
                """.formatted(requestIdJson, targetPort, mode, totalCount, maxConcurrencyJson, intervalJson, deadlineJson));
    }

    private Long runId(ResponseEntity<String> startResponse) throws Exception {
        return objectMapper.readTree(startResponse.getBody()).path("id").asLong();
    }

    private JsonNode awaitTerminal(Long runId) throws Exception {
        Instant deadline = Instant.now().plusSeconds(6);
        while (Instant.now().isBefore(deadline)) {
            ResponseEntity<String> response = restTemplate.getForEntity("/api/http/batch-runs/" + runId, String.class);
            JsonNode body = objectMapper.readTree(response.getBody());
            if (!"RUNNING".equals(body.path("status").asText())) {
                return body;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("HTTP 批次執行未在預期時間內完成");
    }

    private JsonNode awaitCancelledItem(Long runId) throws Exception {
        Instant deadline = Instant.now().plusSeconds(6);
        while (Instant.now().isBefore(deadline)) {
            ResponseEntity<String> response = restTemplate.getForEntity("/api/http/batch-runs/" + runId, String.class);
            JsonNode body = objectMapper.readTree(response.getBody());
            if ("CANCELLED".equals(body.path("status").asText()) && body.path("cancelledCount").asInt() >= 1) {
                return body;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("HTTP 批次取消項目未在預期時間內完成");
    }

    private JsonNode getItems(Long runId) throws Exception {
        return getItems(runId, 0, 100);
    }

    private JsonNode getItems(Long runId, int page, int size) throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/http/batch-runs/" + runId + "/items?page=" + page + "&size=" + size,
                String.class
        );
        return objectMapper.readTree(response.getBody());
    }

    private ResponseEntity<String> postJson(String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private static void writeResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void sleepForSlowResponse() throws IOException {
        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(3));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("測試 HTTP Server 已中斷。", exception);
        }
    }

    private static final class TargetServer implements AutoCloseable {

        private final HttpServer server;
        private final ExecutorService executor;

        private TargetServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static TargetServer start(HttpHandler handler) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newCachedThreadPool();
            server.setExecutor(executor);
            server.createContext("/batch", handler);
            server.start();
            return new TargetServer(server, executor);
        }

        int port() {
            return server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
