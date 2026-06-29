package com.postbubi.proto;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.postbubi.web.dto.ProtoInspectResponse;
import com.postbubi.web.dto.ProtoListResponse;
import com.postbubi.web.dto.ProtoRpcDefinition;
import com.postbubi.web.dto.ProtoServiceDefinition;
import com.postbubi.web.dto.ProtoUploadResponse;
import com.postbubi.web.error.ApiException;

@Service
public class ProtoStorageService {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z0-9_.]+)\\s*;");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("(?m)^\\s*import\\s+(?:public\\s+|weak\\s+)?\"([^\"]+)\"\\s*;");
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("(?m)^\\s*message\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{");
    private static final Pattern SERVICE_PATTERN = Pattern.compile("(?s)service\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{(.*?)\\n\\s*\\}");
    private static final Pattern RPC_PATTERN = Pattern.compile(
            "rpc\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(\\s*(stream\\s+)?([A-Za-z0-9_.]+)\\s*\\)\\s*returns\\s*\\(\\s*(stream\\s+)?([A-Za-z0-9_.]+)\\s*\\)",
            Pattern.MULTILINE
    );

    private final Path protosDir;

    public ProtoStorageService(@Value("${post-bubi.storage.protos-dir:./data/protos}") String protosDir) {
        this.protosDir = Path.of(protosDir).toAbsolutePath().normalize();
    }

    public ProtoUploadResponse store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("PROTO_FILE_REQUIRED", "請選擇要上傳的 .proto 檔。");
        }

        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        if (!originalFilename.endsWith(".proto")) {
            throw badRequest("PROTO_FILE_EXTENSION_INVALID", "只支援上傳 .proto 檔。");
        }

        String protoId = UUID.randomUUID().toString();
        String storedFilename = protoId + "-" + originalFilename;
        Path target = resolveStoredProto(storedFilename);

        try {
            Files.createDirectories(protosDir);
            file.transferTo(target);
            return new ProtoUploadResponse(protoId, originalFilename, storedFilename, file.getSize());
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PROTO_STORE_FAILED",
                    "Proto 檔儲存失敗。",
                    java.util.Map.of("reason", exception.getMessage())
            );
        }
    }

    public List<ProtoListResponse> list() {
        try {
            Files.createDirectories(protosDir);
            try (var stream = Files.list(protosDir)) {
                return stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".proto"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .map(this::toListResponse)
                        .toList();
            }
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PROTO_LIST_FAILED",
                    "Proto 列表讀取失敗。",
                    java.util.Map.of("reason", exception.getMessage())
            );
        }
    }

    public List<StoredProtoFile> listStoredProtosForArchive() {
        try {
            Files.createDirectories(protosDir);
            try (var stream = Files.list(protosDir)) {
                return stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".proto"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .map(this::toStoredProtoFile)
                        .toList();
            }
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PROTO_LIST_FAILED",
                    "Proto 列表讀取失敗。",
                    java.util.Map.of("reason", exception.getMessage())
            );
        }
    }

    public ProtoUploadResponse storeImportedProto(String originalFilename, InputStream inputStream) {
        String normalizedFilename = sanitizeFilename(originalFilename);
        if (!normalizedFilename.endsWith(".proto")) {
            throw badRequest("PROTO_FILE_EXTENSION_INVALID", "只支援上傳 .proto 檔。");
        }

        String protoId = UUID.randomUUID().toString();
        String storedFilename = protoId + "-" + normalizedFilename;
        Path target = resolveStoredProto(storedFilename);

        try {
            Files.createDirectories(protosDir);
            Files.copy(inputStream, target);
            return new ProtoUploadResponse(protoId, normalizedFilename, storedFilename, Files.size(target));
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PROTO_IMPORT_FAILED",
                    "匯入 Proto 檔儲存失敗。",
                    java.util.Map.of("reason", exception.getMessage())
            );
        }
    }

    public ProtoInspectResponse inspect(String protoId) {
        Path file = findProto(protoId);
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PROTO_READ_FAILED",
                    "Proto 檔讀取失敗。",
                    java.util.Map.of("reason", exception.getMessage())
            );
        }

        return new ProtoInspectResponse(
                protoIdFromFilename(file.getFileName().toString()),
                originalFilename(file.getFileName().toString()),
                firstMatch(PACKAGE_PATTERN, content),
                allMatches(IMPORT_PATTERN, content),
                allMatches(MESSAGE_PATTERN, content),
                parseServices(content)
        );
    }

    private ProtoListResponse toListResponse(Path path) {
        try {
            return new ProtoListResponse(
                    protoIdFromFilename(path.getFileName().toString()),
                    originalFilename(path.getFileName().toString()),
                    Files.size(path),
                    Files.getLastModifiedTime(path).toInstant()
            );
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PROTO_STAT_FAILED",
                    "Proto 檔資訊讀取失敗。",
                    java.util.Map.of("filename", path.getFileName().toString())
            );
        }
    }

    private StoredProtoFile toStoredProtoFile(Path path) {
        String filename = path.getFileName().toString();
        String protoId = protoIdFromFilename(filename);
        String originalFilename = originalFilename(filename);
        String archivePath = "protos/" + protoId + "-" + sanitizeFilename(originalFilename);
        try {
            return new StoredProtoFile(
                    protoId,
                    originalFilename,
                    archivePath,
                    Files.readAllBytes(path)
            );
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PROTO_READ_FAILED",
                    "Proto 檔讀取失敗。",
                    java.util.Map.of("filename", filename)
            );
        }
    }

    private List<ProtoServiceDefinition> parseServices(String content) {
        List<ProtoServiceDefinition> services = new ArrayList<>();
        Matcher serviceMatcher = SERVICE_PATTERN.matcher(content);
        while (serviceMatcher.find()) {
            List<ProtoRpcDefinition> methods = new ArrayList<>();
            Matcher rpcMatcher = RPC_PATTERN.matcher(serviceMatcher.group(2));
            while (rpcMatcher.find()) {
                methods.add(new ProtoRpcDefinition(
                        rpcMatcher.group(1),
                        rpcMatcher.group(3),
                        rpcMatcher.group(5),
                        rpcMatcher.group(2) != null,
                        rpcMatcher.group(4) != null
                ));
            }
            services.add(new ProtoServiceDefinition(serviceMatcher.group(1), methods));
        }
        return services;
    }

    private Path findProto(String protoId) {
        if (protoId == null || protoId.trim().isEmpty()) {
            throw badRequest("PROTO_ID_REQUIRED", "Proto ID 不可空白。");
        }
        try {
            Files.createDirectories(protosDir);
            String prefix = protoId.trim() + "-";
            try (var stream = Files.list(protosDir)) {
                return stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().startsWith(prefix))
                        .filter(path -> path.getFileName().toString().endsWith(".proto"))
                        .findFirst()
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROTO_NOT_FOUND", "找不到指定的 Proto 檔。"));
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PROTO_LOOKUP_FAILED",
                    "Proto 檔查詢失敗。",
                    java.util.Map.of("reason", exception.getMessage())
            );
        }
    }

    private Path resolveStoredProto(String storedFilename) {
        Path target = protosDir.resolve(storedFilename).normalize();
        if (!target.startsWith(protosDir)) {
            throw badRequest("PROTO_PATH_INVALID", "Proto 檔路徑不合法。");
        }
        return target;
    }

    private String sanitizeFilename(String filename) {
        String value = filename == null || filename.trim().isEmpty() ? "schema.proto" : filename.trim();
        value = Path.of(value).getFileName().toString();
        value = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return value.isEmpty() ? "schema.proto" : value;
    }

    private String protoIdFromFilename(String filename) {
        if (filename.length() > 36 && filename.charAt(36) == '-') {
            return filename.substring(0, 36);
        }
        int delimiterIndex = filename.indexOf('-');
        return delimiterIndex < 0 ? filename : filename.substring(0, delimiterIndex);
    }

    private String originalFilename(String filename) {
        int delimiterIndex = filename.length() > 36 && filename.charAt(36) == '-' ? 36 : filename.indexOf('-');
        return delimiterIndex < 0 ? filename : filename.substring(delimiterIndex + 1);
    }

    private String firstMatch(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1) : "";
    }

    private List<String> allMatches(Pattern pattern, String content) {
        List<String> result = new ArrayList<>();
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public record StoredProtoFile(String protoId, String originalFilename, String path, byte[] content) {
    }
}
