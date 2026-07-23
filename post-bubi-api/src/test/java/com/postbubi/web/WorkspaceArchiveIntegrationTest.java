package com.postbubi.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
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
                "post-bubi.storage.files-dir=./build/test-files/workspace-archive-integration/files",
                "post-bubi.storage.protos-dir=./build/test-files/workspace-archive-integration/protos"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WorkspaceArchiveIntegrationTest {

    private static final Path STORAGE_DIR = Path.of("./build/test-files/workspace-archive-integration");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanStorage() throws Exception {
        if (!Files.exists(STORAGE_DIR)) {
            return;
        }
        try (var stream = Files.walk(STORAGE_DIR)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void exportsAndImportsWorkspaceZipWithFolderRequestAndFileReference() throws Exception {
        postJson("/api/environments", """
                {
                  "name": "SIT",
                  "variables": [{"key": "baseUrl", "value": "https://sit.example.internal"}]
                }
                """);
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
        String protoId = objectMapper.readTree(uploadProto("archive.proto", """
                syntax = "proto3";

                package archive.demo;

                message ArchiveRequest {
                  string name = 1;
                }
                """).getBody())
                .path("protoId")
                .asText();
        postJson("/api/requests", """
                {
                  "collectionId": %d,
                  "folderId": %d,
                  "type": "HTTP",
                  "name": "Multipart Upload",
                  "sortOrder": 1,
                  "payloadJson": "{\\"requestType\\":\\"HTTP\\",\\"method\\":\\"POST\\",\\"grpcProtoId\\":\\"%s\\",\\"bodyType\\":\\"form-data\\",\\"formData\\":[{\\"type\\":\\"file\\",\\"name\\":\\"file\\",\\"fileId\\":\\"%s\\",\\"fileName\\":\\"archive-source.txt\\",\\"contentType\\":\\"text/plain\\",\\"enabled\\":true}]}"
                }
                """.formatted(collectionId, folderId, protoId, fileId));

        ResponseEntity<byte[]> exportResponse = restTemplate.getForEntity("/api/workspace/export", byte[].class);
        assertThat(exportResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exportResponse.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("application/zip"));

        byte[] zipBytes = exportResponse.getBody();
        assertThat(zipBytes).isNotEmpty();
        Set<String> zipEntries = zipEntries(zipBytes);
        assertThat(zipEntries).contains(
                "collection.json",
                "files/" + fileId + "-archive-source.txt",
                "protos/" + protoId + "-archive.proto"
        );
        JsonNode archive = collectionJson(zipBytes);
        assertThat(archive.path("schemaVersion").asInt()).isEqualTo(3);
        assertThat(archive.path("archiveType").asText()).isEqualTo("WORKSPACE");
        assertThat(archive.path("environments")).isEmpty();

        JsonNode importResult = objectMapper.readTree(importWorkspace(zipBytes).getBody());
        assertThat(importResult.path("collections").asInt()).isEqualTo(1);
        assertThat(importResult.path("folders").asInt()).isEqualTo(1);
        assertThat(importResult.path("requests").asInt()).isEqualTo(1);
        assertThat(importResult.path("protos").asInt()).isEqualTo(1);
        assertThat(importResult.path("environments").asInt()).isZero();

        JsonNode collections = objectMapper.readTree(restTemplate.getForEntity("/api/collections", String.class).getBody());
        assertThat(collections).hasSize(2);
        JsonNode imported = collections.get(1);
        assertThat(imported.path("name").asText()).isEqualTo("匯出測試 Collection 匯入 2");
        assertThat(imported.path("folders")).hasSize(1);
        assertThat(imported.path("requests")).hasSize(1);
        assertThat(imported.path("requests").get(0).path("folderId").asLong())
                .isEqualTo(imported.path("folders").get(0).path("id").asLong());

        JsonNode payload = objectMapper.readTree(imported.path("requests").get(0).path("payloadJson").asText());
        JsonNode importedPart = payload.path("formData").get(0);
        assertThat(importedPart.path("fileId").asText()).isNotBlank();
        assertThat(importedPart.path("fileId").asText()).isNotEqualTo(fileId);
        assertThat(importedPart.has("archivePath")).isFalse();
        assertThat(payload.path("grpcProtoId").asText()).isNotEqualTo(protoId);

        JsonNode protos = objectMapper.readTree(restTemplate.getForEntity("/api/protos", String.class).getBody());
        assertThat(protos).hasSize(2);
        assertThat(protos.get(1).path("filename").asText()).isEqualTo("archive.proto");

        JsonNode environments = objectMapper.readTree(restTemplate.getForEntity("/api/environments", String.class).getBody());
        assertThat(environments).hasSize(1);
        assertThat(environments.get(0).path("name").asText()).isEqualTo("SIT");
    }

    @Test
    void exportsAndImportsSingleCollectionWithoutEnvironmentOrOtherCollections() throws Exception {
        postJson("/api/environments", """
                {"name": "SIT", "variables": [{"key": "baseUrl", "value": "https://sit.example.internal"}]}
                """);
        long exportedCollectionId = postJson("/api/collections", """
                {"name": "單獨匯出", "description": "only this collection"}
                """).path("id").asLong();
        postJson("/api/requests", """
                {"collectionId": %d, "folderId": null, "type": "HTTP", "name": "只在這裡", "sortOrder": 1, "payloadJson": "{\\"requestType\\":\\"HTTP\\"}"}
                """.formatted(exportedCollectionId));
        long unrelatedCollectionId = postJson("/api/collections", """
                {"name": "不應匯出", "description": "other"}
                """).path("id").asLong();
        postJson("/api/requests", """
                {"collectionId": %d, "folderId": null, "type": "HTTP", "name": "不應出現", "sortOrder": 1, "payloadJson": "{\\"requestType\\":\\"HTTP\\"}"}
                """.formatted(unrelatedCollectionId));

        ResponseEntity<byte[]> exportResponse = restTemplate.getForEntity("/api/collections/{id}/export", byte[].class, exportedCollectionId);
        assertThat(exportResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode archive = collectionJson(exportResponse.getBody());
        assertThat(archive.path("archiveType").asText()).isEqualTo("COLLECTION");
        assertThat(archive.path("collections")).hasSize(1);
        assertThat(archive.path("collections").get(0).path("name").asText()).isEqualTo("單獨匯出");
        assertThat(archive.path("requests")).hasSize(1);
        assertThat(archive.path("requests").get(0).path("name").asText()).isEqualTo("只在這裡");
        assertThat(archive.path("environments")).isEmpty();

        JsonNode result = objectMapper.readTree(importWorkspace(exportResponse.getBody()).getBody());
        assertThat(result.path("collections").asInt()).isEqualTo(1);
        assertThat(result.path("environments").asInt()).isZero();

        JsonNode collections = objectMapper.readTree(restTemplate.getForEntity("/api/collections", String.class).getBody());
        assertThat(collections).hasSize(3);
        JsonNode imported = java.util.stream.StreamSupport.stream(collections.spliterator(), false)
                .filter(collection -> "單獨匯出 匯入 2".equals(collection.path("name").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(imported.path("requests")).hasSize(1);
        assertThat(imported.path("requests").get(0).path("name").asText()).isEqualTo("只在這裡");
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

    @Test
    void importsLegacySchemaV1WorkspaceWithoutEnvironments() throws Exception {
        byte[] zipBytes = zipWithCollectionJson("""
                {
                  "schemaVersion": 1,
                  "exportedAt": "2026-07-11T00:00:00Z",
                  "collections": [{"id": 1, "name": "Legacy", "description": "v1"}],
                  "folders": [],
                  "requests": [],
                  "files": [],
                  "protos": []
                }
                """);

        JsonNode result = objectMapper.readTree(importWorkspace(zipBytes).getBody());
        assertThat(result.path("collections").asInt()).isEqualTo(1);
        assertThat(result.path("environments").asInt()).isZero();
    }

    @Test
    void importsLegacySchemaV2WorkspaceWithEnvironments() throws Exception {
        byte[] zipBytes = zipWithCollectionJson("""
                {
                  "schemaVersion": 2,
                  "exportedAt": "2026-07-11T00:00:00Z",
                  "collections": [], "folders": [], "requests": [], "files": [], "protos": [],
                  "environments": [{"name": "Legacy SIT", "variables": [{"key": "baseUrl", "value": "https://legacy.example"}]}]
                }
                """);

        JsonNode result = objectMapper.readTree(importWorkspace(zipBytes).getBody());
        assertThat(result.path("environments").asInt()).isEqualTo(1);
        JsonNode environments = objectMapper.readTree(restTemplate.getForEntity("/api/environments", String.class).getBody());
        assertThat(environments).hasSize(1);
        assertThat(environments.get(0).path("name").asText()).isEqualTo("Legacy SIT");
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

    private ResponseEntity<String> uploadProto(String filename, String content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new NamedByteArrayResource(filename, content.getBytes()));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.exchange("/api/protos", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
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

    private JsonNode collectionJson(byte[] zipBytes) throws Exception {
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if ("collection.json".equals(entry.getName())) {
                    return objectMapper.readTree(zipInputStream.readAllBytes());
                }
            }
        }
        throw new IllegalStateException("collection.json 不存在");
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

    private byte[] zipWithCollectionJson(String collectionJson) throws Exception {
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zipOutputStream = new java.util.zip.ZipOutputStream(outputStream)) {
            zipOutputStream.putNextEntry(new ZipEntry("collection.json"));
            zipOutputStream.write(collectionJson.getBytes());
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
