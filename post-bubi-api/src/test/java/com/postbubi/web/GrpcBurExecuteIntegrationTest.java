package com.postbubi.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:postbubi-grpc-bur-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "post-bubi.bur.code-table-dir=./build/test-files/missing-code-table"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class GrpcBurExecuteIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void previewsComposedBurPayload() throws Exception {
        ResponseEntity<String> response = postJson("/api/grpc-bur/preview", """
                {
                  "tcpipHeaderHex": "0F 0F",
                  "mcsHeader": "A",
                  "basicLabel": "B",
                  "textArea": "C",
                  "settings": {
                    "mcsHeaderLength": 3,
                    "basicLabelLength": 4,
                    "textAreaLength": 2,
                    "padTextAreaRight": true
                  }
                }
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode payload = objectMapper.readTree(response.getBody());
        assertThat(payload.path("tcpipHeaderLength").asInt()).isEqualTo(2);
        assertThat(payload.path("mcsHeaderLength").asInt()).isEqualTo(3);
        assertThat(payload.path("basicLabelLength").asInt()).isEqualTo(4);
        assertThat(payload.path("textAreaLength").asInt()).isEqualTo(2);
        assertThat(payload.path("payloadLength").asInt()).isEqualTo(11);
        assertThat(payload.path("payloadHex").asText()).isEqualTo("0F 0F 41 20 20 C2 40 40 40 C3 40");
        assertThat(payload.path("codec").asText()).isEqualTo("TBConvert BUR CodeTable");
    }

    @Test
    void rejectsTooLongBasicLabel() throws Exception {
        ResponseEntity<String> response = postJson("/api/grpc-bur/preview", """
                {
                  "basicLabel": "ABCDE",
                  "settings": {
                    "basicLabelLength": 4
                  }
                }
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode error = objectMapper.readTree(response.getBody());
        assertThat(error.path("code").asText()).isEqualTo("GRPC_BUR_TEXT_TOO_LONG");
        assertThat(error.path("message").asText()).contains("Basic Label");
    }

    @Test
    void previewsAutomatedBasicLabelSequenceAndTaiwanDayOfMonth() throws Exception {
        String basicLabel = basicLabel("0000009");
        ResponseEntity<String> response = postJson("/api/grpc-bur/preview", """
                {
                  "tcpipHeaderHex": "0F 0F",
                  "basicLabel": "%s",
                  "settings": {
                    "basicLabelLength": 100,
                    "incrementBasicLabelSequence": true,
                    "fillBasicLabelDayOfMonth": true
                  }
                }
                """.formatted(basicLabel));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String expected = replaceAt(basicLabel, 7, 14, "0000010");
        expected = replaceAt(expected, 87, 89, LocalDate.now(ZoneId.of("Asia/Taipei")).format(java.time.format.DateTimeFormatter.ofPattern("dd")));
        assertThat(objectMapper.readTree(response.getBody()).path("effectiveBasicLabel").asText()).isEqualTo(expected);
    }

    @Test
    void previewsBasicLabelWithBatchSequenceOffset() throws Exception {
        String basicLabel = basicLabel("0000009");
        ResponseEntity<String> response = postJson("/api/grpc-bur/preview", """
                {
                  "tcpipHeaderHex": "0F 0F",
                  "basicLabel": "%s",
                  "basicLabelSequenceIncrement": 3,
                  "settings": {
                    "basicLabelLength": 100,
                    "incrementBasicLabelSequence": true
                  }
                }
                """.formatted(basicLabel));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(response.getBody()).path("effectiveBasicLabel").asText())
                .isEqualTo(replaceAt(basicLabel, 7, 14, "0000012"));
    }

    @Test
    void rejectsTimeoutOutsideSupportedRange() throws Exception {
        ResponseEntity<String> response = postJson("/api/grpc-bur/execute", """
                {
                  "host": "127.0.0.1",
                  "port": "1",
                  "timeoutMillis": 0
                }
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode error = objectMapper.readTree(response.getBody());
        assertThat(error.path("code").asText()).isEqualTo("GRPC_TIMEOUT_INVALID");
    }

    private ResponseEntity<String> postJson(String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(path, new HttpEntity<>(body, headers), String.class);
    }

    private String basicLabel(String sequence) {
        char[] characters = new char[100];
        Arrays.fill(characters, 'A');
        System.arraycopy(sequence.toCharArray(), 0, characters, 7, 7);
        return new String(characters);
    }

    private String replaceAt(String value, int start, int end, String replacement) {
        return value.substring(0, start) + replacement + value.substring(end);
    }
}
