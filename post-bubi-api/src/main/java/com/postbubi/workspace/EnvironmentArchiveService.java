package com.postbubi.workspace;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postbubi.service.EnvironmentService;
import com.postbubi.service.EnvironmentService.StoredEnvironment;
import com.postbubi.web.dto.EnvironmentResponse;
import com.postbubi.web.error.ApiException;

@Service
public class EnvironmentArchiveService {

    private static final int SCHEMA_VERSION = 3;
    private static final String ENVIRONMENT_JSON = "environment.json";
    private static final String ARCHIVE_TYPE_ENVIRONMENT = "ENVIRONMENT";

    private final EnvironmentService environmentService;
    private final ObjectMapper objectMapper;

    public EnvironmentArchiveService(EnvironmentService environmentService, ObjectMapper objectMapper) {
        this.environmentService = environmentService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public byte[] exportEnvironment(Long id) {
        EnvironmentArchive archive = new EnvironmentArchive(
                SCHEMA_VERSION,
                ARCHIVE_TYPE_ENVIRONMENT,
                Instant.now().toString(),
                environmentService.getForArchive(id)
        );
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            zipOutputStream.putNextEntry(new ZipEntry(ENVIRONMENT_JSON));
            zipOutputStream.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(archive));
            zipOutputStream.closeEntry();
            zipOutputStream.finish();
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ENVIRONMENT_EXPORT_FAILED", "Environment 匯出失敗。", Map.of("reason", exception.getMessage()));
        }
    }

    @Transactional
    public EnvironmentResponse importEnvironment(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("ENVIRONMENT_IMPORT_FILE_REQUIRED", "請選擇要匯入的 ZIP 檔。");
        }
        byte[] environmentJson = readEnvironmentJson(file);
        EnvironmentArchive archive;
        try {
            archive = objectMapper.readValue(environmentJson, EnvironmentArchive.class);
        } catch (IOException exception) {
            throw badRequest("ENVIRONMENT_IMPORT_JSON_INVALID", "environment.json 格式錯誤。");
        }
        if (archive.schemaVersion() != SCHEMA_VERSION || !ARCHIVE_TYPE_ENVIRONMENT.equals(archive.archiveType()) || archive.environment() == null) {
            throw badRequest("ENVIRONMENT_IMPORT_SCHEMA_NOT_SUPPORTED", "不支援的 Environment 匯入檔格式。");
        }
        return environmentService.importArchivedEnvironment(archive.environment());
    }

    private byte[] readEnvironmentJson(MultipartFile file) {
        try (ZipInputStream zipInputStream = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.startsWith("/") || name.contains("..")) {
                    throw badRequest("ENVIRONMENT_IMPORT_PATH_INVALID", "ZIP 內含不合法路徑。");
                }
                if (ENVIRONMENT_JSON.equals(name)) {
                    return zipInputStream.readAllBytes();
                }
            }
        } catch (IOException exception) {
            throw badRequest("ENVIRONMENT_IMPORT_ZIP_INVALID", "ZIP 檔讀取失敗。");
        }
        throw badRequest("ENVIRONMENT_IMPORT_JSON_REQUIRED", "ZIP 必須包含 environment.json。");
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private record EnvironmentArchive(int schemaVersion, String archiveType, String exportedAt, StoredEnvironment environment) {
    }
}
