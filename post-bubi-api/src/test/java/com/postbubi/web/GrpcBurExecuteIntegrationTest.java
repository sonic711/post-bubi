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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.List;
import java.util.HexFormat;

import com.postbubi.grpc.GrpcResponsePayloadDecoder;
import com.postbubi.grpcbur.BurCodecService;

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

    @Autowired
    private BurCodecService burCodecService;

    @Autowired
    private GrpcResponsePayloadDecoder responsePayloadDecoder;

    @Test
    void decodesOnlyBurResponsePayloadsToUtf8() throws Exception {
        String text = "繁體測試";
        String body = objectMapper.writeValueAsString(Map.of(
                "payload", Map.of(
                        "0", Map.of(
                                "charsets", "BUR",
                                "format", "TEXT",
                                "data", Base64.getEncoder().encodeToString(burCodecService.encode(text))
                        ),
                        "1", Map.of(
                                "charsets", "UTF8",
                                "format", "TEXT",
                                "data", Base64.getEncoder().encodeToString("not BUR".getBytes(StandardCharsets.UTF_8))
                        )
                )
        ));

        var decoded = responsePayloadDecoder.decodeBurPayloads(body);

        assertThat(decoded).hasSize(1);
        assertThat(decoded.get(0).key()).isEqualTo("0");
        assertThat(decoded.get(0).text()).isEqualTo(text);
        assertThat(decoded.get(0).error()).isEmpty();
    }

    @Test
    void retainsIndividualBurDecodeErrorsWithoutDiscardingOtherPayloads() throws Exception {
        var body = objectMapper.createObjectNode();
        var payload = body.putObject("payload");
        payload.putObject("invalid").put("charsets", "BUR").put("data", "!invalid!");
        payload.putObject("missing").put("charsets", "BUR");
        payload.putObject("good").put("charsets", " bur ")
                .put("data", Base64.getEncoder().encodeToString(burCodecService.encode("繁體測試")));
        var decoded = responsePayloadDecoder.decodeBurPayloads(body.toString());
        assertThat(decoded).hasSize(3);
        assertThat(decoded.stream().filter(item -> !item.error().isEmpty())).hasSize(2);
        assertThat(decoded.stream().filter(item -> item.key().equals("good")).findFirst().orElseThrow().text()).isEqualTo("繁體測試");
        assertThat(responsePayloadDecoder.decodeBurPayloads("not json")).isEmpty();
    }

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
    void previewsA98AndA99WithoutMcsAndOptionallyUpdatesPackedDecimalLength() throws Exception {
        for (String[] sample : List.of(
                new String[] {"983029400020734AU07000001 A98100500", "A98  0001002222", "68"},
                new String[] {"981045800001372AI07000001 A99100410", "A99  2", "59"})) {
            for (boolean autoLength : List.of(false, true)) {
                var request = totaBoxRequest(sample[0], sample[1]);
                request.with("settings").put("autoUpdateTcpipLength", autoLength);
                var response = postJson("/api/grpc-bur/preview", request.toString());
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                var preview = objectMapper.readTree(response.getBody());
                int length = Integer.parseInt(sample[2]);
                assertThat(preview.path("messageLayout").asText()).isEqualTo("TOTA_BOX");
                assertThat(preview.path("totaBoxLength").asInt()).isEqualTo(6);
                assertThat(preview.path("mcsHeaderLength").asInt()).isZero();
                assertThat(preview.path("basicLabelLength").asInt()).isEqualTo(35);
                assertThat(preview.path("textAreaLength").asInt()).isEqualTo(sample[1].length());
                assertThat(preview.path("payloadLength").asInt()).isEqualTo(length);
                byte[] bytes = HexFormat.of().parseHex(preview.path("payloadHex").asText().replace(" ", ""));
                assertThat(Arrays.copyOfRange(bytes, 0, 12)).isEqualTo(HexFormat.of().parseHex(
                        "0f0f0f" + (autoLength ? String.format("%06d", length) : "000612") + "010022231f0f"));
                assertThat(Arrays.copyOfRange(bytes, 12, 18)).containsOnly((byte) 0);
                assertThat(burCodecService.decode(Arrays.copyOfRange(bytes, 18, bytes.length))).isEqualTo(sample[0] + sample[1]);
            }
        }
    }

    @Test
    void preservesRawTotaBoxAndUsesBurByteLengthRatherThanUtf8Length() throws Exception {
        var request = totaBoxRequest("A", "繁體");
        request.with("settings").put("basicLabelLength", 1).put("totaBoxHex", "00 01 02 FE FD FF")
                .put("autoUpdateTcpipLength", true);
        var response = postJson("/api/grpc-bur/preview", request.toString());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var preview = objectMapper.readTree(response.getBody());
        byte[] bytes = HexFormat.of().parseHex(preview.path("payloadHex").asText().replace(" ", ""));
        assertThat(Arrays.copyOfRange(bytes, 12, 18)).isEqualTo(HexFormat.of().parseHex("000102fefdff"));
        assertThat(bytes.length).isEqualTo(19 + burCodecService.encode("繁體").length);
        assertThat(HexFormat.of().formatHex(Arrays.copyOfRange(bytes, 3, 6))).isEqualTo(String.format("%06d", bytes.length));
    }

    @Test
    void rejectsInvalidLayoutsTotaBoxesAndAutomaticLengthHeaders() throws Exception {
        var invalid = totaBoxRequest("A", "B");
        invalid.with("settings").put("messageLayout", "UNKNOWN");
        assertPreviewError(invalid, "GRPC_BUR_LAYOUT_INVALID");
        invalid = totaBoxRequest("A", "B");
        invalid.with("settings").put("totaBoxHex", "00 00");
        assertPreviewError(invalid, "GRPC_BUR_TOTA_BOX_LENGTH_INVALID");
        invalid.with("settings").put("totaBoxHex", "GG 00 00 00 00 00");
        assertPreviewError(invalid, "GRPC_BUR_HEX_INVALID");
        for (String header : List.of("0F 0F", "00 0F 0F 00 06 12 01 00 22 23 1F 0F",
                "0F 0F 0F 00 06 12 00 00 22 23 1F 0F", "0F 0F 0F 00 06 12 01 00 22 23 1F 00")) {
            invalid = totaBoxRequest("A", "B");
            invalid.put("tcpipHeaderHex", header);
            invalid.with("settings").put("autoUpdateTcpipLength", true);
            assertPreviewError(invalid, "GRPC_BUR_TCPIP_HEADER_INVALID");
        }
        invalid = totaBoxRequest("A", "A".repeat(999982));
        invalid.with("settings").put("autoUpdateTcpipLength", true);
        assertPreviewError(invalid, "GRPC_BUR_PAYLOAD_LENGTH_OVERFLOW");
    }

    @Test
    void retainsLegacyMcsDefaultsAndRejectsShortLabelRuleRanges() throws Exception {
        var request = totaBoxRequest("A", "B");
        request.with("settings").remove("messageLayout");
        request.with("settings").put("totaBoxHex", "not used");
        request.remove("mcsHeader");
        var response = postJson("/api/grpc-bur/preview", request.toString());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var preview = objectMapper.readTree(response.getBody());
        assertThat(preview.path("messageLayout").asText()).isEqualTo("MCS");
        assertThat(preview.path("mcsHeaderLength").asInt()).isEqualTo(72);
        assertThat(preview.path("totaBoxLength").asInt()).isZero();
        assertThat(preview.path("payloadLength").asInt()).isEqualTo(120);
        assertThat(preview.path("payloadHex").asText()).startsWith("0F 0F 0F 00 06 12 01 00 22 23 1F 0F 20 20");
        request = totaBoxRequest("A", "B");
        request.with("settings").putArray("fieldRules").addObject().put("id", "out-of-range").put("name", "範圍")
                .put("enabled", true).put("color", "#AB005F").put("action", "NONE")
                .put("targetType", "BUR_BASIC_LABEL_RANGE").put("start", 37).put("end", 46);
        var rejected = postJson("/api/grpc-bur/preview", request.toString());
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(rejected.getBody()).path("message").asText()).contains("範圍");
    }

    private com.fasterxml.jackson.databind.node.ObjectNode totaBoxRequest(String label, String text) {
        var request = objectMapper.createObjectNode().put("tcpipHeaderHex", "0F 0F 0F 00 06 12 01 00 22 23 1F 0F")
                .put("basicLabel", label).put("textArea", text).put("mcsHeader", "MUST NOT BE SENT");
        request.putObject("settings").put("messageLayout", "TOTA_BOX").put("basicLabelLength", 35).putArray("fieldRules");
        return request;
    }

    private void assertPreviewError(JsonNode request, String code) throws Exception {
        var response = postJson("/api/grpc-bur/preview", request.toString());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(response.getBody()).path("code").asText()).isEqualTo(code);
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
