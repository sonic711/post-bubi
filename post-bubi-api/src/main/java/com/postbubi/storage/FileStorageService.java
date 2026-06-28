package com.postbubi.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.postbubi.web.dto.FileUploadResponse;
import com.postbubi.web.error.ApiException;

@Service
public class FileStorageService {

    private final Path filesDir;

    public FileStorageService(@Value("${post-bubi.storage.files-dir:./data/files}") String filesDir) {
        this.filesDir = Path.of(filesDir).toAbsolutePath().normalize();
    }

    public FileUploadResponse store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("FILE_REQUIRED", "請選擇要上傳的檔案。");
        }

        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String fileId = UUID.randomUUID().toString();
        String storedFilename = fileId + "-" + originalFilename;
        Path target = resolveStoredFile(storedFilename);

        try {
            Files.createDirectories(filesDir);
            file.transferTo(target);
            return new FileUploadResponse(
                    fileId,
                    originalFilename,
                    storedFilename,
                    normalizeContentType(file.getContentType()),
                    file.getSize()
            );
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "FILE_STORE_FAILED",
                    "檔案儲存失敗。",
                    java.util.Map.of("reason", exception.getMessage())
            );
        }
    }

    public Path findUploadedFile(String fileId) {
        if (fileId == null || fileId.trim().isEmpty()) {
            throw badRequest("FILE_ID_REQUIRED", "File ID 不可空白。");
        }

        try {
            Files.createDirectories(filesDir);
            String prefix = fileId.trim() + "-";
            try (var stream = Files.list(filesDir)) {
                return stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().startsWith(prefix))
                        .findFirst()
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "找不到指定的上傳檔案。"));
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "FILE_LOOKUP_FAILED",
                    "檔案查詢失敗。",
                    java.util.Map.of("reason", exception.getMessage())
            );
        }
    }

    public FileUploadResponse storeImportedFile(String originalFilename, String contentType, InputStream inputStream) {
        String normalizedFilename = sanitizeFilename(originalFilename);
        String fileId = UUID.randomUUID().toString();
        String storedFilename = fileId + "-" + normalizedFilename;
        Path target = resolveStoredFile(storedFilename);

        try {
            Files.createDirectories(filesDir);
            Files.copy(inputStream, target);
            return new FileUploadResponse(
                    fileId,
                    normalizedFilename,
                    storedFilename,
                    normalizeContentType(contentType),
                    Files.size(target)
            );
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "FILE_IMPORT_FAILED",
                    "匯入檔案儲存失敗。",
                    java.util.Map.of("reason", exception.getMessage())
            );
        }
    }

    private Path resolveStoredFile(String storedFilename) {
        Path target = filesDir.resolve(storedFilename).normalize();
        if (!target.startsWith(filesDir)) {
            throw badRequest("FILE_PATH_INVALID", "檔案路徑不合法。");
        }
        return target;
    }

    private String sanitizeFilename(String filename) {
        String value = filename == null || filename.trim().isEmpty() ? "upload.bin" : filename.trim();
        value = Path.of(value).getFileName().toString();
        value = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return value.isEmpty() ? "upload.bin" : value;
    }

    private String normalizeContentType(String contentType) {
        return contentType == null || contentType.trim().isEmpty() ? "application/octet-stream" : contentType.trim();
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
