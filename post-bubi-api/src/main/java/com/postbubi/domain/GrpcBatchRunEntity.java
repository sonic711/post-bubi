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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "grpc_batch_runs")
public class GrpcBatchRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id")
    private Long requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GrpcBatchProtocol protocol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HttpBatchMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HttpBatchRunStatus status;

    @Column(name = "total_count", nullable = false)
    private Integer totalCount;

    @Column(name = "max_concurrency", nullable = false)
    private Integer maxConcurrency;

    @Column(name = "interval_millis")
    private Integer intervalMillis;

    @Column(name = "deadline_millis")
    private Integer deadlineMillis;

    @Lob
    @Column(name = "request_snapshot_json", nullable = false)
    private String requestSnapshotJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public GrpcBatchProtocol getProtocol() { return protocol; }
    public void setProtocol(GrpcBatchProtocol protocol) { this.protocol = protocol; }
    public HttpBatchMode getMode() { return mode; }
    public void setMode(HttpBatchMode mode) { this.mode = mode; }
    public HttpBatchRunStatus getStatus() { return status; }
    public void setStatus(HttpBatchRunStatus status) { this.status = status; }
    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    public Integer getMaxConcurrency() { return maxConcurrency; }
    public void setMaxConcurrency(Integer maxConcurrency) { this.maxConcurrency = maxConcurrency; }
    public Integer getIntervalMillis() { return intervalMillis; }
    public void setIntervalMillis(Integer intervalMillis) { this.intervalMillis = intervalMillis; }
    public Integer getDeadlineMillis() { return deadlineMillis; }
    public void setDeadlineMillis(Integer deadlineMillis) { this.deadlineMillis = deadlineMillis; }
    public String getRequestSnapshotJson() { return requestSnapshotJson; }
    public void setRequestSnapshotJson(String requestSnapshotJson) { this.requestSnapshotJson = requestSnapshotJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
