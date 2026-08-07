package com.postbubi.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "http_batch_items")
public class HttpBatchItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_run_id", nullable = false)
    private Long batchRunId;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HttpBatchItemStatus status;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "reason_phrase", length = 1000)
    private String reasonPhrase;

    @Column(name = "duration_millis")
    private Long durationMillis;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Lob
    @Column(name = "response_headers_json")
    private String responseHeadersJson;

    @Lob
    @Column(name = "response_body_preview")
    private String responseBodyPreview;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public Long getId() {
        return id;
    }

    public Long getBatchRunId() {
        return batchRunId;
    }

    public void setBatchRunId(Long batchRunId) {
        this.batchRunId = batchRunId;
    }

    public Integer getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Integer sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public HttpBatchItemStatus getStatus() {
        return status;
    }

    public void setStatus(HttpBatchItemStatus status) {
        this.status = status;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public String getReasonPhrase() {
        return reasonPhrase;
    }

    public void setReasonPhrase(String reasonPhrase) {
        this.reasonPhrase = reasonPhrase;
    }

    public Long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(Long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getResponseHeadersJson() {
        return responseHeadersJson;
    }

    public void setResponseHeadersJson(String responseHeadersJson) {
        this.responseHeadersJson = responseHeadersJson;
    }

    public String getResponseBodyPreview() {
        return responseBodyPreview;
    }

    public void setResponseBodyPreview(String responseBodyPreview) {
        this.responseBodyPreview = responseBodyPreview;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
