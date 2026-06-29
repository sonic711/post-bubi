package com.postbubi.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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
                "spring.datasource.url=jdbc:h2:mem:postbubi-archive-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "post-bubi.storage.files-dir=./build/test-files/workspace-archive-integration"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WorkspaceArchiveIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exportsAndImportsWorkspaceZipWithFolderRequestAndFileReference() throws Exception {
        long collectionId = postJson("/api/collections", """
                {
                  "name": "匯出測試 Collection",
                  "description": "archive"
                }
                """).path("id").asLong();
        long folderId = postJson("/api/folders", """
                {
                  "collectionId": %d,
                  "parentFolderId": null,
                  "name": "Uploads",
                  "sortOrder": 1
                }
                """.formatted(collectionId)).path("id").asLong();
        String fileId = objectMapper.readTree(uploadFile("archive-source.txt", "archive payload").getBody())
                .path("fileId")
                .asText();
        postJson("/api/requests", """
                {
                  "collectionId": %d,
                  "folderId": %d,
                  "type": "HTTP",
                  "name": "Multipart Upload",
                  "sortOrder": 1,
                  "payloadJson": "{\\"requestType\\":\\"HTTP\\",\\"method\\":\\"POST\\",\\"bodyType\\":\\"form-data\\",\\"formData\\":[{\\"type\\":\\"file\\",\\"name\\":\\"file\\",\\"fileId\\":\\"%s\\",\\"fileName\\":\\"archive-source.txt\\",\\"contentType\\":\\"text/plain\\",\\"enabled\\":true}]}"
                }
                """.formatted(collectionId, folderId, fileId));

        ResponseEntity<byte[]> exportResponse = restTemplate.getForEntity("/api/workspace/export", byte[].class);
        assertThat(exportResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exportResponse.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("application/zip"));

        byte[] zipBytes = exportResponse.getBody();
        assertThat(zipBytes).isNotEmpty();
        Set<String> zipEntries = zipEntries(zipBytes);
        assertThat(zipEntries).contains("collection.json", "files/" + fileId + "-archive-source.txt");

        JsonNode importResult = objectMapper.readTree(importWorkspace(zipBytes).getBody());
        assertThat(importResult.path("collections").asInt()).isEqualTo(1);
        assertThat(importResult.path("folders").asInt()).isEqualTo(1);
        assertThat(importResult.path("requests").asInt()).isEqualTo(1);

        JsonNode collections = objectMapper.readTree(restTemplate.getForEntity("/api/collections", String.class).getBody());
        assertThat(collections).hasSize(2);
        JsonNode imported = collections.get(1);
        assertThat(imported.path("name").asText()).isEqualTo("匯出測試 Collection 匯入");
        assertThat(imported.path("folders")).hasSize(1);
        assertThat(imported.path("requests")).hasSize(1);
        assertThat(imported.path("requests").get(0).path("folderId").asLong())
                .isEqualTo(imported.path("folders").get(0).path("id").asLong());

        JsonNode payload = objectMapper.readTree(imported.path("requests").get(0).path("payloadJson").asText());
        JsonNode importedPart = payload.path("formData").get(0);
        assertThat(importedPart.path("fileId").asText()).isNotBlank();
        assertThat(importedPart.path("fileId").asText()).isNotEqualTo(fileId);
        assertThat(importedPart.has("archivePath")).isFalse();
    }

    @Test
    void rejectsWorkspaceImportZipWithUnsafePath() throws Exception {
        byte[] zipBytes = zipWithUnsafePath();
        ResponseEntity<String> response = importWorkspace(zipBytes);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode error = objectMapper.readTree(response.getBody());
        assertThat(error.path("code").asText()).isEqualTo("WORKSPACE_IMPORT_PATH_INVALID");
        assertThat(error.path("message").asText()).isEqualTo("ZIP 內含不合法路徑。");
    }

    private JsonNode postJson(String path, String body) throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                path,
                HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(response.getBody());
    }

    private ResponseEntity<String> uploadFile(String filename, String content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new NamedByteArrayResource(filename, content.getBytes()));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.exchange("/api/files", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> importWorkspace(byte[] zipBytes) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new NamedByteArrayResource("workspace.zip", zipBytes));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.exchange("/api/workspace/import", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private Set<String> zipEntries(byte[] zipBytes) throws Exception {
        Set<String> entries = new HashSet<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }
        return entries;
    }

    private byte[] zipWithUnsafePath() throws Exception {
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zipOutputStream = new java.util.zip.ZipOutputStream(outputStream)) {
            zipOutputStream.putNextEntry(new ZipEntry("../collection.json"));
            zipOutputStream.write("{}".getBytes());
            zipOutputStream.closeEntry();
        }
        return outputStream.toByteArray();
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
