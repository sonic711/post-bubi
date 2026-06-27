package com.postbubi.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.postbubi.domain.CollectionEntity;
import com.postbubi.domain.FolderEntity;
import com.postbubi.domain.RequestEntity;
import com.postbubi.domain.RequestType;
import com.postbubi.repository.CollectionRepository;
import com.postbubi.repository.FolderRepository;
import com.postbubi.repository.RequestRepository;
import com.postbubi.web.dto.CollectionCreateRequest;
import com.postbubi.web.dto.CollectionResponse;
import com.postbubi.web.dto.CollectionUpdateRequest;
import com.postbubi.web.dto.FolderCreateRequest;
import com.postbubi.web.dto.FolderResponse;
import com.postbubi.web.dto.FolderUpdateRequest;
import com.postbubi.web.dto.RequestCreateRequest;
import com.postbubi.web.dto.RequestResponse;
import com.postbubi.web.dto.RequestUpdateRequest;
import com.postbubi.web.error.ApiException;

@Service
public class WorkspaceService {

    private static final String DEFAULT_PAYLOAD_JSON = "{}";

    private final CollectionRepository collectionRepository;
    private final FolderRepository folderRepository;
    private final RequestRepository requestRepository;

    public WorkspaceService(
            CollectionRepository collectionRepository,
            FolderRepository folderRepository,
            RequestRepository requestRepository
    ) {
        this.collectionRepository = collectionRepository;
        this.folderRepository = folderRepository;
        this.requestRepository = requestRepository;
    }

    @Transactional(readOnly = true)
    public List<CollectionResponse> listCollections() {
        return collectionRepository.findAll().stream()
                .map(this::toCollectionResponse)
                .toList();
    }

    @Transactional
    public CollectionResponse createCollection(CollectionCreateRequest request) {
        CollectionEntity entity = new CollectionEntity();
        entity.setName(requiredText(request.name(), "name", "Collection 名稱不可空白。"));
        entity.setDescription(trimToNull(request.description()));
        return toCollectionResponse(collectionRepository.saveAndFlush(entity));
    }

    @Transactional
    public CollectionResponse updateCollection(Long id, CollectionUpdateRequest request) {
        CollectionEntity entity = findCollection(id);
        entity.setName(requiredText(request.name(), "name", "Collection 名稱不可空白。"));
        entity.setDescription(trimToNull(request.description()));
        return toCollectionResponse(collectionRepository.saveAndFlush(entity));
    }

    @Transactional
    public void deleteCollection(Long id) {
        CollectionEntity entity = findCollection(id);
        requestRepository.deleteByCollectionId(entity.getId());
        folderRepository.deleteByCollectionId(entity.getId());
        collectionRepository.delete(entity);
    }

    @Transactional
    public FolderResponse createFolder(FolderCreateRequest request) {
        Long collectionId = requiredId(request.collectionId(), "collectionId", "Collection ID 不可空白。");
        findCollection(collectionId);
        validateParentFolder(collectionId, request.parentFolderId());

        FolderEntity entity = new FolderEntity();
        entity.setCollectionId(collectionId);
        entity.setParentFolderId(request.parentFolderId());
        entity.setName(requiredText(request.name(), "name", "Folder 名稱不可空白。"));
        entity.setSortOrder(defaultSortOrder(request.sortOrder()));
        return toFolderResponse(folderRepository.saveAndFlush(entity));
    }

    @Transactional
    public FolderResponse updateFolder(Long id, FolderUpdateRequest request) {
        FolderEntity entity = findFolder(id);
        validateParentFolder(entity.getCollectionId(), request.parentFolderId());

        if (entity.getId().equals(request.parentFolderId())) {
            throw badRequest("INVALID_PARENT_FOLDER", "Folder 不可將自己設為上層 Folder。", "parentFolderId", request.parentFolderId());
        }

        entity.setParentFolderId(request.parentFolderId());
        entity.setName(requiredText(request.name(), "name", "Folder 名稱不可空白。"));
        entity.setSortOrder(defaultSortOrder(request.sortOrder()));
        return toFolderResponse(folderRepository.saveAndFlush(entity));
    }

    @Transactional
    public void deleteFolder(Long id) {
        FolderEntity entity = findFolder(id);
        if (folderRepository.existsByCollectionIdAndParentFolderId(entity.getCollectionId(), entity.getId())) {
            throw badRequest("FOLDER_HAS_CHILDREN", "Folder 仍有子 Folder，請先刪除子 Folder。", "id", id);
        }
        requestRepository.deleteByFolderId(entity.getId());
        folderRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public RequestResponse getRequest(Long id) {
        return toRequestResponse(findRequest(id));
    }

    @Transactional
    public RequestResponse createRequest(RequestCreateRequest request) {
        Long collectionId = requiredId(request.collectionId(), "collectionId", "Collection ID 不可空白。");
        findCollection(collectionId);
        validateFolder(collectionId, request.folderId());

        RequestEntity entity = new RequestEntity();
        entity.setCollectionId(collectionId);
        entity.setFolderId(request.folderId());
        entity.setType(requiredType(request.type()));
        entity.setName(requiredText(request.name(), "name", "Request 名稱不可空白。"));
        entity.setSortOrder(defaultSortOrder(request.sortOrder()));
        entity.setPayloadJson(defaultPayload(request.payloadJson()));
        return toRequestResponse(requestRepository.saveAndFlush(entity));
    }

    @Transactional
    public RequestResponse updateRequest(Long id, RequestUpdateRequest request) {
        RequestEntity entity = findRequest(id);
        validateFolder(entity.getCollectionId(), request.folderId());

        entity.setFolderId(request.folderId());
        entity.setType(requiredType(request.type()));
        entity.setName(requiredText(request.name(), "name", "Request 名稱不可空白。"));
        entity.setSortOrder(defaultSortOrder(request.sortOrder()));
        entity.setPayloadJson(defaultPayload(request.payloadJson()));
        return toRequestResponse(requestRepository.saveAndFlush(entity));
    }

    @Transactional
    public void deleteRequest(Long id) {
        requestRepository.delete(findRequest(id));
    }

    @Transactional
    public RequestResponse duplicateRequest(Long id) {
        RequestEntity source = findRequest(id);
        RequestEntity duplicate = new RequestEntity();
        duplicate.setCollectionId(source.getCollectionId());
        duplicate.setFolderId(source.getFolderId());
        duplicate.setType(source.getType());
        duplicate.setName(source.getName() + " 複本");
        duplicate.setSortOrder(source.getSortOrder() + 1);
        duplicate.setPayloadJson(source.getPayloadJson());
        return toRequestResponse(requestRepository.save(duplicate));
    }

    private CollectionResponse toCollectionResponse(CollectionEntity entity) {
        List<FolderResponse> folders = folderRepository.findByCollectionIdOrderBySortOrderAscIdAsc(entity.getId())
                .stream()
                .map(this::toFolderResponse)
                .toList();
        List<RequestResponse> requests = requestRepository.findByCollectionIdOrderBySortOrderAscIdAsc(entity.getId())
                .stream()
                .map(this::toRequestResponse)
                .toList();
        return new CollectionResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                folders,
                requests,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private FolderResponse toFolderResponse(FolderEntity entity) {
        return new FolderResponse(
                entity.getId(),
                entity.getCollectionId(),
                entity.getParentFolderId(),
                entity.getName(),
                entity.getSortOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private RequestResponse toRequestResponse(RequestEntity entity) {
        return new RequestResponse(
                entity.getId(),
                entity.getCollectionId(),
                entity.getFolderId(),
                entity.getType(),
                entity.getName(),
                entity.getSortOrder(),
                entity.getPayloadJson(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private CollectionEntity findCollection(Long id) {
        return collectionRepository.findById(requiredId(id, "id", "Collection ID 不可空白。"))
                .orElseThrow(() -> notFound("COLLECTION_NOT_FOUND", "找不到指定的 Collection。", "id", id));
    }

    private FolderEntity findFolder(Long id) {
        return folderRepository.findById(requiredId(id, "id", "Folder ID 不可空白。"))
                .orElseThrow(() -> notFound("FOLDER_NOT_FOUND", "找不到指定的 Folder。", "id", id));
    }

    private RequestEntity findRequest(Long id) {
        return requestRepository.findById(requiredId(id, "id", "Request ID 不可空白。"))
                .orElseThrow(() -> notFound("REQUEST_NOT_FOUND", "找不到指定的 Request。", "id", id));
    }

    private void validateParentFolder(Long collectionId, Long parentFolderId) {
        if (parentFolderId == null) {
            return;
        }
        FolderEntity parent = findFolder(parentFolderId);
        if (!collectionId.equals(parent.getCollectionId())) {
            throw badRequest("PARENT_FOLDER_COLLECTION_MISMATCH", "上層 Folder 不屬於指定的 Collection。", "parentFolderId", parentFolderId);
        }
    }

    private void validateFolder(Long collectionId, Long folderId) {
        if (folderId == null) {
            return;
        }
        FolderEntity folder = findFolder(folderId);
        if (!collectionId.equals(folder.getCollectionId())) {
            throw badRequest("FOLDER_COLLECTION_MISMATCH", "Folder 不屬於指定的 Collection。", "folderId", folderId);
        }
    }

    private RequestType requiredType(RequestType type) {
        if (type == null) {
            throw badRequest("REQUIRED_FIELD", "Request 類型不可空白。", "field", "type");
        }
        return type;
    }

    private Long requiredId(Long value, String field, String message) {
        if (value == null) {
            throw badRequest("REQUIRED_FIELD", message, "field", field);
        }
        return value;
    }

    private String requiredText(String value, String field, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw badRequest("REQUIRED_FIELD", message, "field", field);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Integer defaultSortOrder(Integer sortOrder) {
        return sortOrder == null ? 0 : sortOrder;
    }

    private String defaultPayload(String payloadJson) {
        String trimmed = trimToNull(payloadJson);
        return trimmed == null ? DEFAULT_PAYLOAD_JSON : trimmed;
    }

    private ApiException notFound(String code, String message, String detailKey, Object detailValue) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message, Map.of(detailKey, detailValue));
    }

    private ApiException badRequest(String code, String message, String detailKey, Object detailValue) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, Map.of(detailKey, detailValue));
    }
}
