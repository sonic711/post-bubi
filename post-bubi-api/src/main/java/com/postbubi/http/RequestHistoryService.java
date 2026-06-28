package com.postbubi.http;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postbubi.domain.RequestHistoryEntity;
import com.postbubi.repository.RequestHistoryRepository;
import com.postbubi.web.dto.HttpExecuteRequest;
import com.postbubi.web.dto.HttpExecuteResponse;
import com.postbubi.web.dto.RequestHistoryResponse;

@Service
public class RequestHistoryService {

    private static final int RESPONSE_PREVIEW_LIMIT = 4000;
    private static final int ERROR_MESSAGE_LIMIT = 1000;

    private final RequestHistoryRepository requestHistoryRepository;
    private final ObjectMapper objectMapper;

    public RequestHistoryService(RequestHistoryRepository requestHistoryRepository, ObjectMapper objectMapper) {
        this.requestHistoryRepository = requestHistoryRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<RequestHistoryResponse> listRecent() {
        return requestHistoryRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void record(HttpExecuteRequest request, HttpExecuteResponse response, String errorMessage) {
        RequestHistoryEntity entity = new RequestHistoryEntity();
        entity.setRequestId(request.requestId());
        entity.setMethod(trimToDefault(request.method(), "GET").toUpperCase(java.util.Locale.ROOT));
        entity.setUrl(trimToDefault(request.url(), ""));
        entity.setRequestJson(toJson(request));
        entity.setSuccess(errorMessage == null);
        entity.setErrorMessage(limit(errorMessage, ERROR_MESSAGE_LIMIT));

        if (response != null) {
            entity.setStatusCode(response.statusCode());
            entity.setDurationMillis(response.durationMillis());
            entity.setSizeBytes(response.sizeBytes());
            entity.setResponseBodyPreview(limit(response.body(), RESPONSE_PREVIEW_LIMIT));
        }

        requestHistoryRepository.save(entity);
    }

    private RequestHistoryResponse toResponse(RequestHistoryEntity entity) {
        return new RequestHistoryResponse(
                entity.getId(),
                entity.getRequestId(),
                entity.getMethod(),
                entity.getUrl(),
                entity.getStatusCode(),
                entity.getDurationMillis(),
                entity.getSizeBytes(),
                entity.getSuccess(),
                entity.getErrorMessage(),
                entity.getRequestJson(),
                entity.getResponseBodyPreview(),
                entity.getCreatedAt()
        );
    }

    private String toJson(HttpExecuteRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String trimToDefault(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    private String limit(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }
}
