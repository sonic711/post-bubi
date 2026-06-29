package com.postbubi.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:postbubi-proto-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "post-bubi.storage.protos-dir=./build/test-files/proto-integration"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProtoIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void uploadsListsAndInspectsProtoDefinition() throws Exception {
        ResponseEntity<String> uploadResponse = uploadProto("service.proto", """
                syntax = "proto3";

                package com.example.echo;

                import "common/message.proto";

                message EchoRequest {
                  string text = 1;
                }

                message EchoResponse {
                  string text = 1;
                }

                service EchoService {
                  rpc Echo (EchoRequest) returns (EchoResponse) {}
                  rpc Subscribe (EchoRequest) returns (stream EchoResponse) {}
                }
                """);
        assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode upload = objectMapper.readTree(uploadResponse.getBody());
        assertThat(upload.path("protoId").asText()).isNotBlank();
        assertThat(upload.path("originalFilename").asText()).isEqualTo("service.proto");

        JsonNode list = objectMapper.readTree(restTemplate.getForEntity("/api/protos", String.class).getBody());
        assertThat(list).hasSize(1);
        assertThat(list.get(0).path("protoId").asText()).isEqualTo(upload.path("protoId").asText());
        assertThat(list.get(0).path("filename").asText()).isEqualTo("service.proto");

        JsonNode inspect = objectMapper.readTree(restTemplate
                .getForEntity("/api/protos/{protoId}/inspect", String.class, upload.path("protoId").asText())
                .getBody());
        assertThat(inspect.path("packageName").asText()).isEqualTo("com.example.echo");
        assertThat(inspect.path("imports").get(0).asText()).isEqualTo("common/message.proto");
        assertThat(inspect.path("messages")).extracting(JsonNode::asText)
                .containsExactly("EchoRequest", "EchoResponse");
        assertThat(inspect.path("services").get(0).path("name").asText()).isEqualTo("EchoService");
        assertThat(inspect.path("services").get(0).path("methods")).hasSize(2);
        assertThat(inspect.path("services").get(0).path("methods").get(0).path("name").asText()).isEqualTo("Echo");
        assertThat(inspect.path("services").get(0).path("methods").get(0).path("requestType").asText()).isEqualTo("EchoRequest");
        assertThat(inspect.path("services").get(0).path("methods").get(0).path("responseType").asText()).isEqualTo("EchoResponse");
        assertThat(inspect.path("services").get(0).path("methods").get(0).path("serverStreaming").asBoolean()).isFalse();
        assertThat(inspect.path("services").get(0).path("methods").get(1).path("serverStreaming").asBoolean()).isTrue();
    }

    @Test
    void rejectsNonProtoFileUpload() throws Exception {
        ResponseEntity<String> response = uploadProto("schema.txt", "not proto");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode error = objectMapper.readTree(response.getBody());
        assertThat(error.path("code").asText()).isEqualTo("PROTO_FILE_EXTENSION_INVALID");
        assertThat(error.path("message").asText()).isEqualTo("只支援上傳 .proto 檔。");
    }

    private ResponseEntity<String> uploadProto(String filename, String content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new NamedByteArrayResource(filename, content.getBytes()));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.exchange("/api/protos", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private static class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        NamedByteArrayResource(String filename, byte[] byteArray) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
