package com.postbubi.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:field-rule-prepare;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class FieldRulePrepareIntegrationTest {
    @Autowired private TestRestTemplate rest;
    @Autowired private ObjectMapper mapper;

    @Test
    void preparesHttpWithoutNetworkOrHistoryAndExecutesPreparedBodyWithoutDoubleIncrement() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var target = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        target.createContext("/", exchange -> {
            calls.incrementAndGet();
            byte[] body = exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        target.start();
        try {
            int historyCount = mapper.readTree(rest.getForObject("/api/http/history", String.class)).size();
            var request = mapper.createObjectNode().put("method", "POST")
                    .put("url", "http://127.0.0.1:" + target.getAddress().getPort()).put("bodyType", "json")
                    .put("body", "{\"id\":\"0009\"}");
            var rule = addRule(request, "HTTP_JSON_VALUE").put("path", "/id");
            var prepared = post("/api/http/prepare", request);
            assertThat(prepared.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(calls.get()).isZero();
            assertThat(mapper.readTree(rest.getForObject("/api/http/history", String.class)).size()).isEqualTo(historyCount);
            String body = mapper.readTree(prepared.getBody()).path("body").asText();
            assertThat(body).contains("0010");
            request.put("body", body);
            request.set("sourceFieldRules", request.path("fieldRules").deepCopy());
            rule.put("action", "NONE");
            var result = post("/api/http/execute", request);
            assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(calls.get()).isEqualTo(1);
            assertThat(mapper.readTree(result.getBody()).path("body").asText()).contains("0010").doesNotContain("0011");
            var history = mapper.readTree(rest.getForObject("/api/http/history", String.class));
            var recorded = mapper.readTree(history.get(0).path("requestJson").asText());
            assertThat(recorded.path("fieldRules").get(0).path("action").asText()).isEqualTo("NONE");
            assertThat(recorded.path("sourceFieldRules").get(0).path("action").asText()).isEqualTo("INCREMENT");
        } finally {
            target.stop(0);
        }
    }

    @Test
    void grpcPrepareNeedsNoReachableTargetAndRejectsInvalidUtf8Ranges() throws Exception {
        var request = mapper.createObjectNode().put("host", "127.0.0.1").put("port", 1)
                .put("body", "{\"payload\":{\"data\":\"繁0009\"}}").put("encodePayloadDataBase64", true);
        var rule = addRule(request, "GRPC_PAYLOAD_DATA_RANGE").put("start", 4).put("end", 7);
        var result = post("/api/grpc/prepare", request);
        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(mapper.readTree(result.getBody()).path("body").asText()).contains("繁0010");
        rule.put("start", 2).put("end", 4);
        var invalid = post("/api/grpc/prepare", request);
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        assertThat(mapper.readTree(invalid.getBody()).path("code").asText()).isEqualTo("FIELD_RULE_UTF8_BOUNDARY_INVALID");
    }

    private ObjectNode addRule(ObjectNode request, String targetType) {
        return request.putArray("fieldRules").addObject().put("id", "sequence").put("name", "序號")
                .put("enabled", true).put("color", "#AB005F").put("action", "INCREMENT").put("targetType", targetType);
    }

    private org.springframework.http.ResponseEntity<String> post(String path, ObjectNode request) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity(path, new HttpEntity<>(request.toString(), headers), String.class);
    }
}
