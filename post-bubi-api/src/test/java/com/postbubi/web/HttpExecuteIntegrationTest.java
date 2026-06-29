package com.postbubi.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
                "spring.datasource.url=jdbc:h2:mem:postbubi-http-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class HttpExecuteIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void executesHttpGetAndStoresHistory() throws Exception {
        String executeBody = """
                {
                  "method": "GET",
                  "url": "http://127.0.0.1:%d/api/health",
                  "headers": [
                    {
                      "name": "Accept",
                      "value": "application/json",
                      "enabled": true
                    }
                  ],
                  "params": [
                    {
                      "name": "stage",
                      "value": "http-execute-test",
                      "enabled": true
                    }
                  ],
                  "bodyType": "none",
                  "timeoutMillis": 30000,
                  "followRedirects": true,
                  "ignoreSslVerification": false
                }
                """.formatted(port);

        ResponseEntity<String> executeResponse = postJson("/api/http/execute", executeBody);
        assertThat(executeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode execute = objectMapper.readTree(executeResponse.getBody());
        assertThat(execute.path("statusCode").asInt()).isEqualTo(200);
        assertThat(execute.path("body").asText()).contains("\"status\"");
        assertThat(execute.path("bodyBase64Encoded").asBoolean()).isFalse();

        ResponseEntity<String> historyResponse = restTemplate.getForEntity("/api/http/history", String.class);
        assertThat(historyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode history = objectMapper.readTree(historyResponse.getBody());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).path("method").asText()).isEqualTo("GET");
        assertThat(history.get(0).path("url").asText()).contains("/api/health");
        assertThat(history.get(0).path("statusCode").asInt()).isEqualTo(200);
        assertThat(history.get(0).path("success").asBoolean()).isTrue();
        assertThat(history.get(0).path("responseBodyPreview").asText()).contains("\"status\"");
    }

    @Test
    void returnsStructuredErrorForInvalidUrl() throws Exception {
        ResponseEntity<String> response = postJson("/api/http/execute", """
                {
                  "method": "GET",
                  "url": "not-a-url",
                  "bodyType": "none"
                }
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode error = objectMapper.readTree(response.getBody());
        assertThat(error.path("code").asText()).isEqualTo("HTTP_URL_INVALID");
        assertThat(error.path("message").asText()).isEqualTo("URL 必須包含 scheme 與 host，例如 http://localhost:18080/api/health。");
        assertThat(error.path("details").isEmpty()).isTrue();
    }

    private ResponseEntity<String> postJson(String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }
}
