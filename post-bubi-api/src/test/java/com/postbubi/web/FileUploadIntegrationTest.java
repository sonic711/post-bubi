package com.postbubi.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
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
                "spring.datasource.url=jdbc:h2:mem:postbubi-file-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "post-bubi.storage.files-dir=./build/test-files/file-upload-integration"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FileUploadIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void uploadsFileAndSendsItAgainWithHttpExecuteFormData() throws Exception {
        JsonNode upload = objectMapper.readTree(uploadFile("source.txt", "hello file upload").getBody());
        assertThat(upload.path("fileId").asText()).isNotBlank();
        assertThat(upload.path("originalFilename").asText()).isEqualTo("source.txt");
        assertThat(upload.path("contentType").asText()).isEqualTo(MediaType.TEXT_PLAIN_VALUE);
        assertThat(upload.path("sizeBytes").asLong()).isEqualTo("hello file upload".getBytes().length);

        ResponseEntity<String> executeResponse = postJson("/api/http/execute", """
                {
                  "method": "POST",
                  "url": "http://127.0.0.1:%d/api/files",
                  "headers": [
                    {
                      "name": "Content-Type",
                      "value": "multipart/form-data",
                      "enabled": true
                    }
                  ],
                  "bodyType": "form-data",
                  "formData": [
                    {
                      "type": "file",
                      "name": "file",
                      "fileId": "%s",
                      "fileName": "execute-sample.txt",
                      "contentType": "text/plain",
                      "enabled": true
                    }
                  ],
                  "timeoutMillis": 30000,
                  "followRedirects": true,
                  "ignoreSslVerification": false
                }
                """.formatted(port, upload.path("fileId").asText()));

        assertThat(executeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode execute = objectMapper.readTree(executeResponse.getBody());
        assertThat(execute.path("statusCode").asInt()).isEqualTo(201);
        assertThat(execute.path("body").asText()).contains("\"originalFilename\":\"execute-sample.txt\"");
    }

    @Test
    void returnsStructuredErrorWhenFormDataHasNoEnabledPart() throws Exception {
        ResponseEntity<String> response = postJson("/api/http/execute", """
                {
                  "method": "POST",
                  "url": "http://127.0.0.1:%d/api/files",
                  "bodyType": "form-data",
                  "formData": []
                }
                """.formatted(port));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode error = objectMapper.readTree(response.getBody());
        assertThat(error.path("code").asText()).isEqualTo("HTTP_FORM_DATA_REQUIRED");
        assertThat(error.path("message").asText()).isEqualTo("form-data 至少需要一個啟用的欄位。");
    }

    private ResponseEntity<String> uploadFile(String filename, String content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new NamedByteArrayResource(filename, content.getBytes()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.exchange("/api/files", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> postJson(String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
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
