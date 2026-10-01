package com.postbubi.grpc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postbubi.domain.GrpcBatchItemEntity;
import com.postbubi.domain.GrpcBatchProtocol;
import com.postbubi.domain.GrpcBatchRunEntity;
import com.postbubi.domain.HttpBatchItemStatus;
import com.postbubi.domain.HttpBatchMode;
import com.postbubi.domain.HttpBatchRunStatus;
import com.postbubi.execution.ExecutionCancellationService;
import com.postbubi.execution.ExecutionCancellationService.ExecutionHandle;
import com.postbubi.grpcbur.GrpcBurExecuteService;
import com.postbubi.repository.GrpcBatchItemRepository;
import com.postbubi.repository.GrpcBatchRunRepository;
import com.postbubi.web.dto.GrpcBatchClearResponse;
import com.postbubi.web.dto.GrpcBatchItemPageResponse;
import com.postbubi.web.dto.GrpcBatchItemResponse;
import com.postbubi.web.dto.GrpcBatchRunPageResponse;
import com.postbubi.web.dto.GrpcBatchRunResponse;
import com.postbubi.web.dto.GrpcBatchStartRequest;
import com.postbubi.web.dto.GrpcBurBatchStartRequest;
import com.postbubi.web.dto.GrpcBurExecuteRequest;
import com.postbubi.web.dto.GrpcBurExecuteResponse;
import com.postbubi.web.dto.GrpcExecuteRequest;
import com.postbubi.web.dto.GrpcExecuteResponse;
import com.postbubi.web.dto.HttpNameValue;
import com.postbubi.web.error.ApiException;

import jakarta.annotation.PreDestroy;

/** Persists and executes unary gRPC and gRPC BUR batch snapshots without touching HTTP batch data. */
@Service
public class GrpcBatchService {

    private static final int MAX_CONCURRENCY = 100;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_MAX_CONCURRENCY = 1;
    private static final int DEFAULT_TIMEOUT_MILLIS = 30000;
    private static final int MAX_INTERVAL_MILLIS = 300000;
    private static final int MIN_DEADLINE_MILLIS = 1000;
    private static final int MAX_DEADLINE_MILLIS = 3600000;
    private static final int RESPONSE_BODY_PREVIEW_LIMIT = 4000;
    private static final int ERROR_MESSAGE_LIMIT = 2000;
    private static final DateTimeFormatter CSV_TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS XXX")
            .withZone(ZoneId.of("Asia/Taipei"));

    private final GrpcExecuteService grpcExecuteService;
    private final GrpcBurExecuteService grpcBurExecuteService;
    private final ExecutionCancellationService executionCancellationService;
    private final GrpcBatchRunRepository batchRunRepository;
    private final GrpcBatchItemRepository batchItemRepository;
    private final ObjectMapper objectMapper;
    private final ExecutorService runExecutor = Executors.newFixedThreadPool(2);
    private final ExecutorService itemExecutor = Executors.newFixedThreadPool(MAX_CONCURRENCY);
    private final ConcurrentMap<Long, ActiveBatch> activeBatches = new ConcurrentHashMap<>();

    public GrpcBatchService(
            GrpcExecuteService grpcExecuteService,
            GrpcBurExecuteService grpcBurExecuteService,
            ExecutionCancellationService executionCancellationService,
            GrpcBatchRunRepository batchRunRepository,
            GrpcBatchItemRepository batchItemRepository,
            ObjectMapper objectMapper
    ) {
        this.grpcExecuteService = grpcExecuteService;
        this.grpcBurExecuteService = grpcBurExecuteService;
        this.executionCancellationService = executionCancellationService;
        this.batchRunRepository = batchRunRepository;
        this.batchItemRepository = batchItemRepository;
        this.objectMapper = objectMapper;
    }

    public GrpcBatchRunResponse startGrpc(GrpcBatchStartRequest request) {
        if (request == null || request.grpcRequest() == null) {
            throw badRequest("GRPC_BATCH_REQUEST_REQUIRED", "批次執行需要 gRPC 請求內容。");
        }
        return start(GrpcBatchProtocol.GRPC, request.requestId(), withoutExecutionId(request.grpcRequest()),
                request.mode(), request.totalCount(), request.maxConcurrency(), request.intervalMillis(), request.deadlineMillis());
    }

    public GrpcBatchRunResponse startGrpcBur(GrpcBurBatchStartRequest request) {
        if (request == null || request.grpcBurRequest() == null) {
            throw badRequest("GRPC_BUR_BATCH_REQUEST_REQUIRED", "批次執行需要 gRPC BUR 請求內容。");
        }
        return start(GrpcBatchProtocol.GRPC_BUR, request.requestId(), withoutExecutionId(request.grpcBurRequest()),
                request.mode(), request.totalCount(), request.maxConcurrency(), request.intervalMillis(), request.deadlineMillis());
    }

    public GrpcBatchRunResponse getRun(GrpcBatchProtocol protocol, Long batchRunId) {
        GrpcBatchRunEntity run = findRun(protocol, batchRunId);
        return toRunResponse(run, batchItemRepository.findByBatchRunIdOrderBySequenceNumberAsc(batchRunId));
    }

    public GrpcBatchRunPageResponse listRuns(GrpcBatchProtocol protocol, Long requestId, int page, int size) {
        validatePage(page, size);
        Page<GrpcBatchRunEntity> result = requestId == null
                ? batchRunRepository.findByProtocolOrderByCreatedAtDesc(protocol, PageRequest.of(page, size))
                : batchRunRepository.findByProtocolAndRequestIdOrderByCreatedAtDesc(protocol, requestId, PageRequest.of(page, size));
        return new GrpcBatchRunPageResponse(result.getContent().stream().map(run -> toRunResponse(
                run, batchItemRepository.findByBatchRunIdOrderBySequenceNumberAsc(run.getId()))).toList(), page, size, result.getTotalElements());
    }

    public GrpcBatchItemPageResponse listItems(GrpcBatchProtocol protocol, Long batchRunId, int page, int size) {
        validatePage(page, size);
        findRun(protocol, batchRunId);
        Page<GrpcBatchItemEntity> result = batchItemRepository.findByBatchRunIdOrderBySequenceNumberAsc(batchRunId, PageRequest.of(page, size));
        return new GrpcBatchItemPageResponse(result.getContent().stream().map(this::toItemResponse).toList(), page, size, result.getTotalElements());
    }

    public GrpcBatchRunResponse cancel(GrpcBatchProtocol protocol, Long batchRunId) {
        GrpcBatchRunEntity run = findRun(protocol, batchRunId);
        if (run.getStatus() != HttpBatchRunStatus.RUNNING) {
            return toRunResponse(run, batchItemRepository.findByBatchRunIdOrderBySequenceNumberAsc(batchRunId));
        }
        activeBatches.computeIfAbsent(batchRunId, ignored -> new ActiveBatch())
                .stop(StopReason.CANCELLED, executionCancellationService);
        return toRunResponse(run, batchItemRepository.findByBatchRunIdOrderBySequenceNumberAsc(batchRunId));
    }

    @Transactional
    public GrpcBatchClearResponse clearCompletedRuns(GrpcBatchProtocol protocol, Long requestId) {
        if (requestId == null) {
            throw badRequest("GRPC_BATCH_REQUEST_ID_REQUIRED", "清除批次記錄需要 Request ID。");
        }
        List<GrpcBatchRunEntity> runs = batchRunRepository.findByProtocolAndRequestIdAndStatusNotOrderByCreatedAtDesc(
                protocol, requestId, HttpBatchRunStatus.RUNNING);
        List<Long> ids = runs.stream().map(GrpcBatchRunEntity::getId).toList();
        if (!ids.isEmpty()) {
            batchItemRepository.deleteByBatchRunIdIn(ids);
            batchRunRepository.deleteAll(runs);
        }
        return new GrpcBatchClearResponse(ids.size());
    }

    public byte[] exportCsv(GrpcBatchProtocol protocol, Long batchRunId) {
        GrpcBatchRunEntity run = findRun(protocol, batchRunId);
        List<GrpcBatchItemEntity> items = batchItemRepository.findByBatchRunIdOrderBySequenceNumberAsc(batchRunId);
        StringBuilder csv = new StringBuilder("\uFEFFbatchRunId,requestId,protocol,createdAt,mode,runStatus,sequenceNumber,itemStatus,itemStartedAt,itemCompletedAt,statusCode,statusDescription,durationMillis,sizeBytes,errorMessage,responseMetadata,responseBodyPreview,decodedPayloads\r\n");
        for (GrpcBatchItemEntity item : items) {
            appendCsvRow(csv, run.getId(), run.getRequestId(), run.getProtocol(), formatTimestamp(run.getCreatedAt()), run.getMode(), run.getStatus(),
                    item.getSequenceNumber(), item.getStatus(), formatTimestamp(item.getStartedAt()), formatTimestamp(item.getCompletedAt()), item.getStatusCode(), item.getStatusDescription(), item.getDurationMillis(),
                    item.getSizeBytes(), item.getErrorMessage(), metadataText(item), item.getResponseBodyPreview(), item.getDecodedPayloadsJson());
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private GrpcBatchRunResponse start(
            GrpcBatchProtocol protocol,
            Long requestId,
            Object snapshot,
            HttpBatchMode mode,
            Integer totalCount,
            Integer maxConcurrency,
            Integer intervalMillis,
            Integer deadlineMillis
    ) {
        NormalizedConfig config = normalize(mode, totalCount, maxConcurrency, intervalMillis, deadlineMillis);
        GrpcBatchRunEntity run = new GrpcBatchRunEntity();
        run.setRequestId(requestId);
        run.setProtocol(protocol);
        run.setMode(config.mode());
        run.setStatus(HttpBatchRunStatus.RUNNING);
        run.setTotalCount(config.totalCount());
        run.setMaxConcurrency(config.maxConcurrency());
        run.setIntervalMillis(config.intervalMillis());
        run.setDeadlineMillis(config.deadlineMillis());
        run.setRequestSnapshotJson(writeJson(snapshot));
        run = batchRunRepository.save(run);

        List<GrpcBatchItemEntity> items = new ArrayList<>();
        for (int sequence = 1; sequence > 0 && sequence <= config.totalCount(); sequence++) {
            GrpcBatchItemEntity item = new GrpcBatchItemEntity();
            item.setBatchRunId(run.getId());
            item.setSequenceNumber(sequence);
            item.setStatus(HttpBatchItemStatus.NOT_DISPATCHED);
            items.add(item);
        }
        batchItemRepository.saveAll(items);
        ActiveBatch active = new ActiveBatch();
        activeBatches.put(run.getId(), active);
        Long runId = run.getId();
        runExecutor.submit(() -> executeRun(runId, protocol, snapshot, active));
        return toRunResponse(run, items);
    }

    private void executeRun(Long batchRunId, GrpcBatchProtocol protocol, Object snapshot, ActiveBatch active) {
        try {
            GrpcBatchRunEntity run = findRun(protocol, batchRunId);
            run.setStartedAt(Instant.now());
            batchRunRepository.save(run);
            List<GrpcBatchItemEntity> items = batchItemRepository.findByBatchRunIdOrderBySequenceNumberAsc(batchRunId);
            long deadlineNanos = run.getMode() == HttpBatchMode.DEADLINE
                    ? System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(run.getDeadlineMillis()) : Long.MAX_VALUE;
            if (run.getMode() == HttpBatchMode.RESPONSE_INTERVAL) {
                executeWithResponseInterval(run, items, snapshot, active, deadlineNanos);
            } else {
                executeWithConcurrency(run, items, snapshot, active, deadlineNanos);
            }
        } finally {
            completeRun(batchRunId, protocol, active);
            activeBatches.remove(batchRunId);
        }
    }

    private void executeWithResponseInterval(GrpcBatchRunEntity run, List<GrpcBatchItemEntity> items, Object snapshot, ActiveBatch active, long deadlineNanos) {
        for (GrpcBatchItemEntity item : items) {
            if (!canStart(active, deadlineNanos)) {
                return;
            }
            executeItem(run, item.getId(), snapshot, active, deadlineNanos);
            if (!active.isStopped() && item.getSequenceNumber() < run.getTotalCount()) {
                sleepUntilNext(active, run.getIntervalMillis());
            }
        }
    }

    private void executeWithConcurrency(GrpcBatchRunEntity run, List<GrpcBatchItemEntity> items, Object snapshot, ActiveBatch active, long deadlineNanos) {
        ExecutorCompletionService<Void> completion = new ExecutorCompletionService<>(itemExecutor);
        int next = 0;
        int inProgress = 0;
        while (next < items.size() && inProgress < run.getMaxConcurrency() && canStart(active, deadlineNanos)) {
            submitItem(completion, run, items.get(next++), snapshot, active, deadlineNanos);
            inProgress++;
        }
        while (inProgress > 0) {
            Future<Void> completed = waitForCompletion(completion, deadlineNanos, active);
            if (completed == null) {
                if (!active.isStopped()) {
                    active.stop(StopReason.DEADLINE, executionCancellationService);
                }
                break;
            }
            inProgress--;
            consume(completed);
            while (next < items.size() && inProgress < run.getMaxConcurrency() && canStart(active, deadlineNanos)) {
                submitItem(completion, run, items.get(next++), snapshot, active, deadlineNanos);
                inProgress++;
            }
        }
        if (active.isStopped()) {
            active.cancelChildren(executionCancellationService);
        }
        while (inProgress-- > 0) {
            try {
                consume(completion.take());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                active.stop(StopReason.CANCELLED, executionCancellationService);
                return;
            }
        }
    }

    private void submitItem(ExecutorCompletionService<Void> completion, GrpcBatchRunEntity run, GrpcBatchItemEntity item, Object snapshot, ActiveBatch active, long deadlineNanos) {
        completion.submit(() -> {
            executeItem(run, item.getId(), snapshot, active, deadlineNanos);
            return null;
        });
    }

    private Future<Void> waitForCompletion(ExecutorCompletionService<Void> completion, long deadlineNanos, ActiveBatch active) {
        try {
            if (deadlineNanos == Long.MAX_VALUE) {
                return completion.take();
            }
            long remaining = deadlineNanos - System.nanoTime();
            return remaining <= 0 ? null : completion.poll(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            active.stop(StopReason.CANCELLED, executionCancellationService);
            return null;
        }
    }

    private void consume(Future<Void> completed) {
        try {
            completed.get();
        } catch (Exception ignored) {
            // Each item persists its own result before its task completes.
        }
    }

    private void executeItem(GrpcBatchRunEntity run, Long itemId, Object snapshot, ActiveBatch active, long deadlineNanos) {
        GrpcBatchItemEntity item = batchItemRepository.findById(itemId).orElseThrow();
        if (!canStart(active, deadlineNanos)) {
            return;
        }
        item.setStatus(HttpBatchItemStatus.RUNNING);
        item.setStartedAt(Instant.now());
        batchItemRepository.save(item);
        String executionId = "grpc-batch-" + run.getId() + "-" + item.getSequenceNumber();
        ExecutionHandle execution = executionCancellationService.start(executionId);
        active.addChild(executionId);
        try {
            if (active.isStopped()) {
                executionCancellationService.cancel(executionId);
            }
            BatchExecutionResult result = executeSnapshot(run.getProtocol(), snapshot, executionId, remainingTimeout(snapshot, deadlineNanos), item.getSequenceNumber(), execution);
            item.setStatus(statusFor(result.statusCode(), execution, active));
            item.setStatusCode(result.statusCode());
            item.setStatusDescription(truncate(result.statusDescription(), ERROR_MESSAGE_LIMIT));
            item.setDurationMillis(result.durationMillis());
            item.setSizeBytes(result.body() == null ? 0L : (long) result.body().getBytes(StandardCharsets.UTF_8).length);
            item.setErrorMessage(truncate(result.errorMessage(), ERROR_MESSAGE_LIMIT));
            item.setResponseMetadataJson(writeJson(result.metadata()));
            item.setResponseBodyPreview(truncate(result.body(), RESPONSE_BODY_PREVIEW_LIMIT));
            item.setDecodedPayloadsJson(result.decodedPayloads() == null ? null : writeJson(result.decodedPayloads()));
        } catch (ApiException exception) {
            if (deadlineReached(deadlineNanos)) {
                active.stop(StopReason.DEADLINE, executionCancellationService);
            }
            item.setStatus(execution.isCancelled() || active.isStopped() ? HttpBatchItemStatus.CANCELLED : HttpBatchItemStatus.FAILED);
            item.setErrorMessage(truncate(errorMessage(exception), ERROR_MESSAGE_LIMIT));
        } catch (RuntimeException exception) {
            item.setStatus(active.isStopped() ? HttpBatchItemStatus.CANCELLED : HttpBatchItemStatus.FAILED);
            item.setErrorMessage(truncate(errorMessage(exception), ERROR_MESSAGE_LIMIT));
        } finally {
            item.setCompletedAt(Instant.now());
            batchItemRepository.save(item);
            active.removeChild(executionId);
            executionCancellationService.finish(execution);
        }
    }

    private BatchExecutionResult executeSnapshot(GrpcBatchProtocol protocol, Object snapshot, String executionId, int timeoutMillis, int sequenceNumber, ExecutionHandle execution) {
        if (protocol == GrpcBatchProtocol.GRPC) {
            GrpcExecuteResponse response = grpcExecuteService.execute(withExecution((GrpcExecuteRequest) snapshot, executionId, timeoutMillis), execution);
            return new BatchExecutionResult(response.statusCode(), response.statusDescription(), response.durationMillis(), response.metadata(), response.body(), response.errorMessage(), null);
        }
        GrpcBurExecuteResponse response = grpcBurExecuteService.execute(withExecution((GrpcBurExecuteRequest) snapshot, executionId, timeoutMillis, sequenceNumber), execution);
        return new BatchExecutionResult(response.statusCode(), response.statusDescription(), response.durationMillis(), response.metadata(), response.body(), response.errorMessage(), response.decodedPayloads());
    }

    private HttpBatchItemStatus statusFor(String statusCode, ExecutionHandle execution, ActiveBatch active) {
        if (execution.isCancelled() || active.isStopped() || "CANCELLED".equals(statusCode)) {
            return HttpBatchItemStatus.CANCELLED;
        }
        return "OK".equals(statusCode) ? HttpBatchItemStatus.SUCCESS : HttpBatchItemStatus.FAILED;
    }

    private boolean canStart(ActiveBatch active, long deadlineNanos) {
        if (active.isStopped()) {
            return false;
        }
        if (deadlineNanos != Long.MAX_VALUE && System.nanoTime() >= deadlineNanos) {
            active.stop(StopReason.DEADLINE, executionCancellationService);
            return false;
        }
        return true;
    }

    private void sleepUntilNext(ActiveBatch active, int intervalMillis) {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(intervalMillis);
        while (!active.isStopped()) {
            long remaining = end - System.nanoTime();
            if (remaining <= 0) {
                return;
            }
            try {
                TimeUnit.NANOSECONDS.sleep(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100)));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                active.stop(StopReason.CANCELLED, executionCancellationService);
                return;
            }
        }
    }

    private void completeRun(Long batchRunId, GrpcBatchProtocol protocol, ActiveBatch active) {
        GrpcBatchRunEntity run = batchRunRepository.findById(batchRunId).orElse(null);
        if (run == null || run.getProtocol() != protocol || run.getStatus() != HttpBatchRunStatus.RUNNING) {
            return;
        }
        run.setStatus(switch (active.stopReason()) {
            case DEADLINE -> HttpBatchRunStatus.DEADLINE_EXCEEDED;
            case CANCELLED -> HttpBatchRunStatus.CANCELLED;
            case NONE -> HttpBatchRunStatus.COMPLETED;
        });
        run.setCompletedAt(Instant.now());
        batchRunRepository.save(run);
    }

    private NormalizedConfig normalize(HttpBatchMode mode, Integer totalCount, Integer maxConcurrency, Integer intervalMillis, Integer deadlineMillis) {
        if (mode == null) {
            throw badRequest("GRPC_BATCH_MODE_REQUIRED", "請選擇批次執行模式。");
        }
        if (totalCount == null || totalCount < 1) {
            throw badRequest("GRPC_BATCH_TOTAL_COUNT_INVALID", "批次總筆數必須為正整數。");
        }
        int concurrency = maxConcurrency == null ? DEFAULT_MAX_CONCURRENCY : maxConcurrency;
        if (concurrency < 1 || concurrency > MAX_CONCURRENCY) {
            throw badRequest("GRPC_BATCH_CONCURRENCY_INVALID", "最大併發數必須介於 1 到 100。");
        }
        if (mode == HttpBatchMode.RESPONSE_INTERVAL) {
            if (intervalMillis == null || intervalMillis < 0 || intervalMillis > MAX_INTERVAL_MILLIS) {
                throw badRequest("GRPC_BATCH_INTERVAL_INVALID", "回應後間隔必須介於 0 到 300000 毫秒。");
            }
            return new NormalizedConfig(mode, totalCount, 1, intervalMillis, null);
        }
        if (mode == HttpBatchMode.DEADLINE) {
            if (deadlineMillis == null || deadlineMillis < MIN_DEADLINE_MILLIS || deadlineMillis > MAX_DEADLINE_MILLIS) {
                throw badRequest("GRPC_BATCH_DEADLINE_INVALID", "完成期限必須介於 1000 到 3600000 毫秒。");
            }
            return new NormalizedConfig(mode, totalCount, concurrency, null, deadlineMillis);
        }
        return new NormalizedConfig(mode, totalCount, concurrency, null, null);
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw badRequest("GRPC_BATCH_PAGE_INVALID", "批次結果分頁參數錯誤。");
        }
    }

    private GrpcBatchRunEntity findRun(GrpcBatchProtocol protocol, Long id) {
        GrpcBatchRunEntity run = batchRunRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GRPC_BATCH_NOT_FOUND", "找不到指定的 gRPC 批次執行。"));
        if (run.getProtocol() != protocol) {
            throw new ApiException(HttpStatus.NOT_FOUND, "GRPC_BATCH_NOT_FOUND", "找不到指定的 gRPC 批次執行。");
        }
        return run;
    }

    private GrpcBatchRunResponse toRunResponse(GrpcBatchRunEntity run, List<GrpcBatchItemEntity> items) {
        int inProgress = count(items, HttpBatchItemStatus.RUNNING);
        int success = count(items, HttpBatchItemStatus.SUCCESS);
        int failed = count(items, HttpBatchItemStatus.FAILED);
        int cancelled = count(items, HttpBatchItemStatus.CANCELLED);
        int notDispatched = count(items, HttpBatchItemStatus.NOT_DISPATCHED);
        List<Long> durations = items.stream().map(GrpcBatchItemEntity::getDurationMillis).filter(value -> value != null).toList();
        Long average = durations.isEmpty() ? null : Math.round(durations.stream().mapToLong(Long::longValue).average().orElse(0));
        Long fastest = durations.isEmpty() ? null : durations.stream().mapToLong(Long::longValue).min().orElseThrow();
        Long slowest = durations.isEmpty() ? null : durations.stream().mapToLong(Long::longValue).max().orElseThrow();
        Long duration = run.getStartedAt() == null ? null : (run.getCompletedAt() == null ? Instant.now() : run.getCompletedAt()).toEpochMilli() - run.getStartedAt().toEpochMilli();
        return new GrpcBatchRunResponse(run.getId(), run.getRequestId(), run.getProtocol(), run.getMode(), run.getStatus(), run.getTotalCount(),
                run.getMaxConcurrency(), run.getIntervalMillis(), run.getDeadlineMillis(), run.getTotalCount() - notDispatched, inProgress,
                success, failed, cancelled, notDispatched, duration, average, fastest, slowest, run.getCreatedAt(), run.getStartedAt(), run.getCompletedAt());
    }

    private GrpcBatchItemResponse toItemResponse(GrpcBatchItemEntity item) {
        return new GrpcBatchItemResponse(item.getId(), item.getSequenceNumber(), item.getStatus(), item.getStatusCode(),
                item.getStatusDescription(), item.getDurationMillis(), item.getSizeBytes(), item.getErrorMessage(),
                readMetadata(item.getResponseMetadataJson()), item.getResponseBodyPreview(), readDecodedPayloads(item.getDecodedPayloadsJson()),
                item.getStartedAt(), item.getCompletedAt());
    }

    private int count(List<GrpcBatchItemEntity> items, HttpBatchItemStatus status) {
        return (int) items.stream().filter(item -> item.getStatus() == status).count();
    }

    private String metadataText(GrpcBatchItemEntity item) {
        return readMetadata(item.getResponseMetadataJson()).stream()
                .map(header -> header.name() + ": " + (header.value() == null ? "" : header.value()))
                .reduce((left, right) -> left + "\n" + right).orElse("");
    }

    private void appendCsvRow(StringBuilder csv, Object... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                csv.append(',');
            }
            String value = values[index] == null ? "" : String.valueOf(values[index]);
            csv.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        csv.append("\r\n");
    }

    private String formatTimestamp(Instant timestamp) {
        return timestamp == null ? null : CSV_TIMESTAMP_FORMAT.format(timestamp);
    }

    private GrpcExecuteRequest withoutExecutionId(GrpcExecuteRequest request) {
        return new GrpcExecuteRequest(null, request.host(), request.port(), request.plaintext(), request.ignoreTlsVerification(), request.metadata(), request.protoId(), request.serviceName(), request.methodName(), request.body(), request.encodePayloadDataBase64(), request.timeoutMillis());
    }

    private GrpcBurExecuteRequest withoutExecutionId(GrpcBurExecuteRequest request) {
        return new GrpcBurExecuteRequest(null, request.host(), request.port(), request.timeoutMillis(), request.plaintext(), request.ignoreTlsVerification(), request.metadataText(), request.protoId(), request.serviceName(), request.methodName(), request.tcpipHeaderHex(), request.mcsHeader(), request.basicLabel(), request.textArea(), request.settings(), null);
    }

    private GrpcExecuteRequest withExecution(GrpcExecuteRequest request, String executionId, int timeoutMillis) {
        return new GrpcExecuteRequest(executionId, request.host(), request.port(), request.plaintext(), request.ignoreTlsVerification(), request.metadata(), request.protoId(), request.serviceName(), request.methodName(), request.body(), request.encodePayloadDataBase64(), timeoutMillis);
    }

    private GrpcBurExecuteRequest withExecution(GrpcBurExecuteRequest request, String executionId, int timeoutMillis, int sequenceNumber) {
        return new GrpcBurExecuteRequest(executionId, request.host(), request.port(), timeoutMillis, request.plaintext(), request.ignoreTlsVerification(), request.metadataText(), request.protoId(), request.serviceName(), request.methodName(), request.tcpipHeaderHex(), request.mcsHeader(), request.basicLabel(), request.textArea(), request.settings(), sequenceNumber);
    }

    private int remainingTimeout(Object snapshot, long deadlineNanos) {
        Integer configured = snapshot instanceof GrpcExecuteRequest request ? request.timeoutMillis() : ((GrpcBurExecuteRequest) snapshot).timeoutMillis();
        int timeout = configured == null ? DEFAULT_TIMEOUT_MILLIS : configured;
        if (deadlineNanos == Long.MAX_VALUE) {
            return timeout;
        }
        long remainingNanos = Math.max(1, deadlineNanos - System.nanoTime());
        long remainingMillis = (remainingNanos + TimeUnit.MILLISECONDS.toNanos(1) - 1) / TimeUnit.MILLISECONDS.toNanos(1);
        return (int) Math.max(1, Math.min(timeout, remainingMillis));
    }

    private boolean deadlineReached(long deadlineNanos) {
        return deadlineNanos != Long.MAX_VALUE && deadlineNanos - System.nanoTime() <= TimeUnit.MILLISECONDS.toNanos(5);
    }

    private List<HttpNameValue> readMetadata(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<List<HttpNameValue>>() { }); }
        catch (Exception ignored) { return List.of(); }
    }

    private List<GrpcBurExecuteResponse.GrpcBurDecodedPayload> readDecodedPayloads(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<List<GrpcBurExecuteResponse.GrpcBurDecodedPayload>>() { }); }
        catch (Exception ignored) { return List.of(); }
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "GRPC_BATCH_SERIALIZE_FAILED", "批次執行資料序列化失敗。", Map.of()); }
    }

    private String errorMessage(Exception exception) {
        if (exception instanceof ApiException apiException && apiException.getDetails().get("reason") != null) {
            return String.valueOf(apiException.getDetails().get("reason"));
        }
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) return value;
        return value.substring(0, limit) + "\n...[內容已截斷]";
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    @PreDestroy
    void shutdownExecutors() {
        runExecutor.shutdownNow();
        itemExecutor.shutdownNow();
    }

    private record NormalizedConfig(HttpBatchMode mode, int totalCount, int maxConcurrency, Integer intervalMillis, Integer deadlineMillis) { }
    private record BatchExecutionResult(String statusCode, String statusDescription, Long durationMillis, List<HttpNameValue> metadata, String body, String errorMessage, List<GrpcBurExecuteResponse.GrpcBurDecodedPayload> decodedPayloads) { }
    private enum StopReason { NONE, CANCELLED, DEADLINE }

    private static final class ActiveBatch {
        private final AtomicReference<StopReason> stopReason = new AtomicReference<>(StopReason.NONE);
        private final Set<String> childExecutionIds = ConcurrentHashMap.newKeySet();
        void addChild(String executionId) { childExecutionIds.add(executionId); }
        void removeChild(String executionId) { childExecutionIds.remove(executionId); }
        boolean isStopped() { return stopReason.get() != StopReason.NONE; }
        StopReason stopReason() { return stopReason.get(); }
        void stop(StopReason reason, ExecutionCancellationService cancellationService) {
            stopReason.compareAndSet(StopReason.NONE, reason);
            cancelChildren(cancellationService);
        }
        void cancelChildren(ExecutionCancellationService cancellationService) {
            for (String executionId : childExecutionIds) cancellationService.cancel(executionId);
        }
    }
}
