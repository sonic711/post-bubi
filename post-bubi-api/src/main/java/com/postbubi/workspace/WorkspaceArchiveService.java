package com.postbubi.workspace;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.postbubi.domain.CollectionEntity;
import com.postbubi.domain.FolderEntity;
import com.postbubi.domain.RequestEntity;
import com.postbubi.domain.RequestType;
import com.postbubi.proto.ProtoStorageService;
import com.postbubi.repository.CollectionRepository;
import com.postbubi.repository.FolderRepository;
import com.postbubi.repository.RequestRepository;
import com.postbubi.service.EnvironmentService;
import com.postbubi.storage.FileStorageService;
import com.postbubi.web.dto.FileUploadResponse;
import com.postbubi.web.error.ApiException;

@Service
public class WorkspaceArchiveService {

    private static final int SCHEMA_VERSION = 2;
    private static final String COLLECTION_JSON = "collection.json";

    private final CollectionRepository collectionRepository;
    private final FolderRepository folderRepository;
    private final RequestRepository requestRepository;
    private final EnvironmentService environmentService;
    private final FileStorageService fileStorageService;
    private final ProtoStorageService protoStorageService;
    private final ObjectMapper objectMapper;

    public WorkspaceArchiveService(
            CollectionRepository collectionRepository,
            FolderRepository folderRepository,
            RequestRepository requestRepository,
            EnvironmentService environmentService,
            FileStorageService fileStorageService,
            ProtoStorageService protoStorageService,
            ObjectMapper objectMapper
    ) {
        this.collectionRepository = collectionRepository;
        this.folderRepository = folderRepository;
        this.requestRepository = requestRepository;
        this.environmentService = environmentService;
        this.fileStorageService = fileStorageService;
        this.protoStorageService = protoStorageService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public byte[] exportWorkspace() {
        Archive archive = buildArchive();
        Map<String, byte[]> fileEntries = collectReferencedFiles(archive.requests());
        Map<String, byte[]> protoEntries = collectProtoFiles(archive.protos());

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            zipOutputStream.putNextEntry(new ZipEntry(COLLECTION_JSON));
            zipOutputStream.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(archive));
            zipOutputStream.closeEntry();

            for (Map.Entry<String, byte[]> entry : fileEntries.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutputStream.write(entry.getValue());
                zipOutputStream.closeEntry();
            }

            for (Map.Entry<String, byte[]> entry : protoEntries.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutputStream.write(entry.getValue());
                zipOutputStream.closeEntry();
            }

            zipOutputStream.putNextEntry(new ZipEntry("protos/"));
            zipOutputStream.closeEntry();
            zipOutputStream.finish();
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "WORKSPACE_EXPORT_FAILED",
                    "Workspace 匯出失敗。",
                    Map.of("reason", exception.getMessage())
            );
        }
    }

    @Transactional
    public ImportResult importWorkspace(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("WORKSPACE_IMPORT_FILE_REQUIRED", "請選擇要匯入的 ZIP 檔。");
        }

        ZipContent zipContent = readZip(file);
        Archive archive = readArchive(zipContent.entries().get(COLLECTION_JSON));
        if (archive.schemaVersion() < 1 || archive.schemaVersion() > SCHEMA_VERSION) {
            throw badRequest("WORKSPACE_SCHEMA_NOT_SUPPORTED", "不支援的匯入檔 schema version。");
        }

        Map<Long, Long> collectionIds = new HashMap<>();
        for (CollectionArchive source : archive.collections()) {
            CollectionEntity entity = new CollectionEntity();
            entity.setName(source.name() + " 匯入");
            entity.setDescription(source.description());
            collectionRepository.saveAndFlush(entity);
            collectionIds.put(source.id(), entity.getId());
        }

        Map<Long, Long> folderIds = importFolders(archive.folders(), collectionIds);
        Map<String, String> fileIds = importFiles(zipContent.entries(), archive.files());
        int protoCount = importProtos(zipContent.entries(), archive.protos());
        int environmentCount = environmentService.importArchivedEnvironments(toStoredEnvironments(archive.environments()));
        int requestCount = importRequests(archive.requests(), collectionIds, folderIds, fileIds);

        return new ImportResult(collectionIds.size(), folderIds.size(), requestCount, protoCount, environmentCount);
    }

    private Archive buildArchive() {
        List<CollectionArchive> collections = collectionRepository.findAll().stream()
                .map(collection -> new CollectionArchive(collection.getId(), collection.getName(), collection.getDescription()))
                .toList();
        List<FolderArchive> folders = folderRepository.findAll().stream()
                .map(folder -> new FolderArchive(
                        folder.getId(),
                        folder.getCollectionId(),
                        folder.getParentFolderId(),
                        folder.getName(),
                        folder.getSortOrder()
                ))
                .toList();
        List<RequestArchive> requests = requestRepository.findAll().stream()
                .map(request -> new RequestArchive(
                        request.getId(),
                        request.getCollectionId(),
                        request.getFolderId(),
                        request.getType(),
                        request.getName(),
                        request.getSortOrder(),
                        replaceFileIdsWithArchivePaths(request.getPayloadJson(), null)
                ))
                .toList();
        List<FileArchive> files = archiveFilesFromRequests(requests);
        List<ProtoArchive> protos = protoStorageService.listStoredProtosForArchive().stream()
                .map(proto -> new ProtoArchive(proto.protoId(), proto.path(), proto.originalFilename()))
                .toList();
        List<EnvironmentArchive> environments = environmentService.listForArchive().stream()
                .map(environment -> new EnvironmentArchive(environment.name(), environment.variables()))
                .toList();
        return new Archive(SCHEMA_VERSION, Instant.now().toString(), collections, folders, requests, files, protos, environments);
    }

    private Map<String, byte[]> collectReferencedFiles(List<RequestArchive> requests) {
        Map<String, byte[]> result = new HashMap<>();
        for (FileArchive file : archiveFilesFromRequests(requests)) {
            try {
                result.put(file.path(), java.nio.file.Files.readAllBytes(fileStorageService.findUploadedFile(file.fileId())));
            } catch (IOException exception) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "WORKSPACE_EXPORT_FILE_FAILED",
                        "匯出檔案讀取失敗。",
                        Map.of("fileId", file.fileId())
                );
            }
        }
        return result;
    }

    private Map<String, byte[]> collectProtoFiles(List<ProtoArchive> protos) {
        Map<String, byte[]> result = new HashMap<>();
        List<ProtoStorageService.StoredProtoFile> storedProtos = protoStorageService.listStoredProtosForArchive();
        Map<String, ProtoStorageService.StoredProtoFile> storedById = new HashMap<>();
        for (ProtoStorageService.StoredProtoFile storedProto : storedProtos) {
            storedById.put(storedProto.protoId(), storedProto);
        }
        for (ProtoArchive proto : safeList(protos)) {
            ProtoStorageService.StoredProtoFile storedProto = storedById.get(proto.protoId());
            if (storedProto != null) {
                result.put(proto.path(), storedProto.content());
            }
        }
        return result;
    }

    private List<FileArchive> archiveFilesFromRequests(List<RequestArchive> requests) {
        Map<String, FileArchive> result = new HashMap<>();
        for (RequestArchive request : requests) {
            for (FileArchive file : extractFileArchives(request.payloadJson())) {
                result.putIfAbsent(file.fileId(), file);
            }
        }
        return new ArrayList<>(result.values());
    }

    private List<FileArchive> extractFileArchives(String payloadJson) {
        List<FileArchive> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            JsonNode formData = root.path("formData");
            if (!formData.isArray()) {
                return result;
            }
            for (JsonNode part : formData) {
                if (!"file".equals(part.path("type").asText())) {
                    continue;
                }
                String fileId = part.path("fileId").asText("");
                if (fileId.isBlank()) {
                    continue;
                }
                String fileName = part.path("fileName").asText(fileId);
                String contentType = part.path("contentType").asText("application/octet-stream");
                result.add(new FileArchive(fileId, "files/" + fileId + "-" + sanitizeArchiveName(fileName), fileName, contentType));
            }
        } catch (IOException ignored) {
            return result;
        }
        return result;
    }

    private String replaceFileIdsWithArchivePaths(String payloadJson, Map<String, String> importedFileIds) {
        try {
            JsonNode rootNode = objectMapper.readTree(payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson);
            if (!(rootNode instanceof ObjectNode root)) {
                return payloadJson;
            }
            JsonNode formData = root.path("formData");
            if (!formData.isArray()) {
                return objectMapper.writeValueAsString(root);
            }
            for (JsonNode part : formData) {
                if (!(part instanceof ObjectNode objectPart) || !"file".equals(part.path("type").asText())) {
                    continue;
                }
                String fileId = part.path("fileId").asText("");
                if (fileId.isBlank()) {
                    continue;
                }
                if (importedFileIds == null) {
                    String fileName = part.path("fileName").asText(fileId);
                    objectPart.put("archivePath", "files/" + fileId + "-" + sanitizeArchiveName(fileName));
                } else if (importedFileIds.containsKey(fileId)) {
                    objectPart.put("fileId", importedFileIds.get(fileId));
                    objectPart.remove("archivePath");
                }
            }
            return objectMapper.writeValueAsString(root);
        } catch (IOException exception) {
            return payloadJson;
        }
    }

    private ZipContent readZip(MultipartFile file) {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.startsWith("/") || name.contains("..")) {
                    throw badRequest("WORKSPACE_IMPORT_PATH_INVALID", "ZIP 內含不合法路徑。");
                }
                entries.put(name, zipInputStream.readAllBytes());
            }
        } catch (IOException exception) {
            throw badRequest("WORKSPACE_IMPORT_ZIP_INVALID", "ZIP 檔讀取失敗。");
        }
        if (!entries.containsKey(COLLECTION_JSON)) {
            throw badRequest("WORKSPACE_COLLECTION_JSON_REQUIRED", "ZIP 必須包含 collection.json。");
        }
        return new ZipContent(entries);
    }

    private Archive readArchive(byte[] collectionJson) {
        try {
            return objectMapper.readValue(collectionJson, Archive.class);
        } catch (IOException exception) {
            throw badRequest("WORKSPACE_COLLECTION_JSON_INVALID", "collection.json 格式錯誤。");
        }
    }

    private Map<Long, Long> importFolders(List<FolderArchive> folders, Map<Long, Long> collectionIds) {
        Map<Long, Long> folderIds = new HashMap<>();
        Set<Long> imported = new HashSet<>();
        while (imported.size() < folders.size()) {
            boolean progressed = false;
            for (FolderArchive source : folders) {
                if (imported.contains(source.id())) {
                    continue;
                }
                Long parentFolderId = source.parentFolderId() == null ? null : folderIds.get(source.parentFolderId());
                if (source.parentFolderId() != null && parentFolderId == null) {
                    continue;
                }
                FolderEntity entity = new FolderEntity();
                entity.setCollectionId(collectionIds.get(source.collectionId()));
                entity.setParentFolderId(parentFolderId);
                entity.setName(source.name());
                entity.setSortOrder(source.sortOrder() == null ? 0 : source.sortOrder());
                folderRepository.saveAndFlush(entity);
                folderIds.put(source.id(), entity.getId());
                imported.add(source.id());
                progressed = true;
            }
            if (!progressed) {
                throw badRequest("WORKSPACE_FOLDER_TREE_INVALID", "Folder 階層資料不合法。");
            }
        }
        return folderIds;
    }

    private Map<String, String> importFiles(Map<String, byte[]> entries, List<FileArchive> files) {
        Map<String, String> fileIds = new HashMap<>();
        for (FileArchive file : safeList(files)) {
            byte[] content = entries.get(file.path());
            if (content == null) {
                continue;
            }
            FileUploadResponse imported = fileStorageService.storeImportedFile(
                    file.originalFilename(),
                    file.contentType(),
                    new ByteArrayInputStream(content)
            );
            fileIds.put(file.fileId(), imported.fileId());
        }
        return fileIds;
    }

    private int importProtos(Map<String, byte[]> entries, List<ProtoArchive> protos) {
        int protoCount = 0;
        for (ProtoArchive proto : safeList(protos)) {
            byte[] content = entries.get(proto.path());
            if (content == null) {
                continue;
            }
            protoStorageService.storeImportedProto(
                    proto.originalFilename(),
                    new ByteArrayInputStream(content)
            );
            protoCount++;
        }
        return protoCount;
    }

    private int importRequests(
            List<RequestArchive> requests,
            Map<Long, Long> collectionIds,
            Map<Long, Long> folderIds,
            Map<String, String> fileIds
    ) {
        int requestCount = 0;
        for (RequestArchive source : requests) {
            RequestEntity entity = new RequestEntity();
            entity.setCollectionId(collectionIds.get(source.collectionId()));
            entity.setFolderId(source.folderId() == null ? null : folderIds.get(source.folderId()));
            entity.setType(source.type());
            entity.setName(source.name());
            entity.setSortOrder(source.sortOrder() == null ? 0 : source.sortOrder());
            entity.setPayloadJson(replaceFileIdsWithArchivePaths(source.payloadJson(), fileIds));
            requestRepository.saveAndFlush(entity);
            requestCount++;
        }
        return requestCount;
    }

    private List<EnvironmentService.StoredEnvironment> toStoredEnvironments(List<EnvironmentArchive> environments) {
        return safeList(environments).stream()
                .map(environment -> new EnvironmentService.StoredEnvironment(environment.name(), environment.variables()))
                .toList();
    }

    private String sanitizeArchiveName(String value) {
        return value == null || value.isBlank() ? "file.bin" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record ImportResult(int collections, int folders, int requests, int protos, int environments) {
    }

    private record ZipContent(Map<String, byte[]> entries) {
    }

    private record Archive(
            int schemaVersion,
            String exportedAt,
            List<CollectionArchive> collections,
            List<FolderArchive> folders,
            List<RequestArchive> requests,
            List<FileArchive> files,
            List<ProtoArchive> protos,
            List<EnvironmentArchive> environments
    ) {
    }

    private record CollectionArchive(Long id, String name, String description) {
    }

    private record FolderArchive(Long id, Long collectionId, Long parentFolderId, String name, Integer sortOrder) {
    }

    private record RequestArchive(
            Long id,
            Long collectionId,
            Long folderId,
            RequestType type,
            String name,
            Integer sortOrder,
            String payloadJson
    ) {
    }

    private record FileArchive(String fileId, String path, String originalFilename, String contentType) {
    }

    private record ProtoArchive(String protoId, String path, String originalFilename) {
    }

    private record EnvironmentArchive(String name, List<com.postbubi.web.dto.EnvironmentVariable> variables) {
    }
}
