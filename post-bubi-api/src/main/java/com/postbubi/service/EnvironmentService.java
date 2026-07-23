package com.postbubi.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.postbubi.domain.EnvironmentEntity;
import com.postbubi.repository.EnvironmentRepository;
import com.postbubi.web.dto.EnvironmentCreateRequest;
import com.postbubi.web.dto.EnvironmentCopyRequest;
import com.postbubi.web.dto.EnvironmentResponse;
import com.postbubi.web.dto.EnvironmentUpdateRequest;
import com.postbubi.web.dto.EnvironmentVariable;
import com.postbubi.web.error.ApiException;

@Service
public class EnvironmentService {

    private static final TypeReference<List<EnvironmentVariable>> VARIABLES_TYPE = new TypeReference<>() {
    };

    private final EnvironmentRepository environmentRepository;
    private final ObjectMapper objectMapper;

    public EnvironmentService(EnvironmentRepository environmentRepository, ObjectMapper objectMapper) {
        this.environmentRepository = environmentRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<EnvironmentResponse> list() {
        return environmentRepository.findAllByOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public EnvironmentResponse create(EnvironmentCreateRequest request) {
        String name = requiredName(request.name());
        if (environmentRepository.existsByNameIgnoreCase(name)) {
            throw badRequest("ENVIRONMENT_NAME_DUPLICATE", "Environment 名稱已存在。", Map.of("name", name));
        }
        EnvironmentEntity entity = new EnvironmentEntity();
        entity.setName(name);
        entity.setVariablesJson(writeVariables(normalizeVariables(request.variables())));
        return toResponse(environmentRepository.saveAndFlush(entity));
    }

    @Transactional
    public EnvironmentResponse update(Long id, EnvironmentUpdateRequest request) {
        EnvironmentEntity entity = find(id);
        String name = requiredName(request.name());
        if (environmentRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw badRequest("ENVIRONMENT_NAME_DUPLICATE", "Environment 名稱已存在。", Map.of("name", name));
        }
        entity.setName(name);
        entity.setVariablesJson(writeVariables(normalizeVariables(request.variables())));
        return toResponse(environmentRepository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        environmentRepository.delete(find(id));
    }

    @Transactional
    public EnvironmentResponse copy(Long id, EnvironmentCopyRequest request) {
        EnvironmentEntity source = find(id);
        String name = requiredName(request == null ? null : request.name());
        if (environmentRepository.existsByNameIgnoreCase(name)) {
            throw badRequest("ENVIRONMENT_NAME_DUPLICATE", "Environment 名稱已存在。", Map.of("name", name));
        }
        EnvironmentEntity entity = new EnvironmentEntity();
        entity.setName(name);
        entity.setVariablesJson(source.getVariablesJson());
        return toResponse(environmentRepository.saveAndFlush(entity));
    }

    @Transactional(readOnly = true)
    public List<StoredEnvironment> listForArchive() {
        return environmentRepository.findAllByOrderByNameAsc().stream()
                .map(entity -> new StoredEnvironment(entity.getName(), readVariables(entity.getVariablesJson())))
                .toList();
    }

    @Transactional(readOnly = true)
    public StoredEnvironment getForArchive(Long id) {
        EnvironmentEntity entity = find(id);
        return new StoredEnvironment(entity.getName(), readVariables(entity.getVariablesJson()));
    }

    @Transactional
    public int importArchivedEnvironments(List<StoredEnvironment> environments) {
        int imported = 0;
        for (StoredEnvironment source : safeList(environments)) {
            saveImportedEnvironment(source);
            imported++;
        }
        environmentRepository.flush();
        return imported;
    }

    @Transactional
    public EnvironmentResponse importArchivedEnvironment(StoredEnvironment environment) {
        return toResponse(saveImportedEnvironment(environment));
    }

    private EnvironmentEntity saveImportedEnvironment(StoredEnvironment source) {
        String baseName = requiredName(source.name());
        EnvironmentEntity entity = new EnvironmentEntity();
        entity.setName(uniqueImportedName(baseName));
        entity.setVariablesJson(writeVariables(normalizeVariables(source.variables())));
        return environmentRepository.save(entity);
    }

    private EnvironmentEntity find(Long id) {
        return environmentRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ENVIRONMENT_NOT_FOUND", "找不到指定的 Environment。", Map.of("id", id)));
    }

    private EnvironmentResponse toResponse(EnvironmentEntity entity) {
        return new EnvironmentResponse(
                entity.getId(),
                entity.getName(),
                readVariables(entity.getVariablesJson()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private List<EnvironmentVariable> normalizeVariables(List<EnvironmentVariable> variables) {
        Set<String> keys = new HashSet<>();
        return safeList(variables).stream()
                .filter(variable -> variable != null)
                .map(variable -> new EnvironmentVariable(trim(variable.key()), variable.value() == null ? "" : variable.value()))
                .filter(variable -> !variable.key().isEmpty())
                .map(variable -> {
                    if (!variable.key().matches("[A-Za-z_][A-Za-z0-9_.-]*")) {
                        throw badRequest("ENVIRONMENT_VARIABLE_KEY_INVALID", "Environment 變數名稱只能包含英文字母、數字、底線、點與連字號，且不可由數字開頭。", Map.of("key", variable.key()));
                    }
                    if (!keys.add(variable.key())) {
                        throw badRequest("ENVIRONMENT_VARIABLE_KEY_DUPLICATE", "Environment 內不可有重複變數名稱。", Map.of("key", variable.key()));
                    }
                    return variable;
                })
                .toList();
    }

    private String requiredName(String value) {
        String name = trim(value);
        if (name.isEmpty()) {
            throw badRequest("REQUIRED_FIELD", "Environment 名稱不可空白。", Map.of("field", "name"));
        }
        return name;
    }

    private String uniqueImportedName(String baseName) {
        if (!environmentRepository.existsByNameIgnoreCase(baseName)) {
            return baseName;
        }
        int suffix = 2;
        while (environmentRepository.existsByNameIgnoreCase(baseName + " 匯入 " + suffix)) {
            suffix++;
        }
        return baseName + " 匯入 " + suffix;
    }

    private String writeVariables(List<EnvironmentVariable> variables) {
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ENVIRONMENT_VARIABLES_WRITE_FAILED", "Environment 變數儲存失敗。");
        }
    }

    private List<EnvironmentVariable> readVariables(String variablesJson) {
        try {
            if (variablesJson == null || variablesJson.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(variablesJson, VARIABLES_TYPE);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ENVIRONMENT_VARIABLES_READ_FAILED", "Environment 變數讀取失敗。");
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private ApiException badRequest(String code, String message, Map<String, Object> details) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, details);
    }

    public record StoredEnvironment(String name, List<EnvironmentVariable> variables) {
    }
}
