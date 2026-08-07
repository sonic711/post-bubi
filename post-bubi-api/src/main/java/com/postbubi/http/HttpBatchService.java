package com.postbubi.http;

import java.time.Instant;
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postbubi.domain.HttpBatchItemEntity;
import com.postbubi.domain.HttpBatchItemStatus;
import com.postbubi.domain.HttpBatchMode;
import com.postbubi.domain.HttpBatchRunEntity;
import com.postbubi.domain.HttpBatchRunStatus;
import com.postbubi.execution.ExecutionCancellationService;
import com.postbubi.execution.ExecutionCancellationService.ExecutionHandle;
import com.postbubi.repository.HttpBatchItemRepository;
import com.postbubi.repository.HttpBatchRunRepository;
import com.postbubi.web.dto.HttpBatchItemPageResponse;
import com.postbubi.web.dto.HttpBatchItemResponse;
import com.postbubi.web.dto.HttpBatchRunPageResponse;
import com.postbubi.web.dto.HttpBatchRunResponse;
import com.postbubi.web.dto.HttpBatchStartRequest;
import com.postbubi.web.dto.HttpExecuteRequest;
import com.postbubi.web.dto.HttpExecuteResponse;
import com.postbubi.web.dto.HttpNameValue;
import com.postbubi.web.error.ApiException;

import jakarta.annotation.PreDestroy;

@Service
public class HttpBatchService {

    private static final int MAX_TOTAL_COUNT = 100;
    private static final int DEFAULT_MAX_CONCURRENCY = 1;
    private static final int MAX_INTERVAL_MILLIS = 300000;
    private static final int MIN_DEADLINE_MILLIS = 1000;
    private static final int MAX_DEADLINE_MILLIS = 3600000;
    private static final int DEFAULT_HTTP_TIMEOUT_MILLIS = 30000;
    private static final int RESPONSE_BODY_PREVIEW_LIMIT = 4000;
    private static final int ERROR_MESSAGE_LIMIT = 2000;

    private final HttpExecuteService httpExecuteService;
    private final ExecutionCancellationService executionCancellationService;
    private final HttpBatchRunRepository batchRunRepository;
    private final HttpBatchItemRepository batchItemRepository;
    private final ObjectMapper objectMapper;
    private final ExecutorService runExecutor = Executors.newFixedThreadPool(2);
    private final ExecutorService itemExecutor = Executors.newFixedThreadPool(MAX_TOTAL_COUNT);
    private final ConcurrentMap<Long, ActiveBatch> activeBatches = new ConcurrentHashMap<>();

    public HttpBatchService(
            HttpExecuteService httpExecuteService,
            ExecutionCancellationService executionCancellationService,
            HttpBatchRunRepository batchRunRepository,
            HttpBatchItemRepository batchItemRepository,
            ObjectMapper objectMapper
    ) {
        this.httpExecuteService = httpExecuteService;
        this.executionCancellationService = executionCancellationService;
        this.batchRunRepository = batchRunRepository;
        this.batchItemRepository = batchItemRepository;
        this.objectMapper = objectMapper;
    }

    public HttpBatchRunResponse start(HttpBatchStartRequest request) {
        NormalizedBatchConfig config = normalizeStartRequest(request);
        HttpExecuteRequest snapshot = withoutExecutionId(config.httpRequest());

        HttpBatchRunEntity run = new HttpBatchRunEntity();
        run.setRequestId(snapshot.requestId());
        run.setMode(config.mode());
        run.setStatus(HttpBatchRunStatus.RUNNING);
        run.setTotalCount(config.totalCount());
        run.setMaxConcurrency(config.maxConcurrency());
        run.setIntervalMillis(config.intervalMillis());
        run.setDeadlineMillis(config.deadlineMillis());
        run.setRequestSnapshotJson(writeJson(snapshot));
        run = batchRunRepository.save(run);

        List<HttpBatchItemEntity> items = new ArrayList<>();
        for (int sequence = 1; sequence <= config.totalCount(); sequence++) {
            HttpBatchItemEntity item = new HttpBatchItemEntity();
            item.setBatchRunId(run.getId());
            item.setSequenceNumber(sequence);
            item.setStatus(HttpBatchItemStatus.NOT_DISPATCHED);
            items.add(item);
        }
        batchItemRepository.saveAll(items);

        ActiveBatch activeBatch = new ActiveBatch();
        activeBatches.put(run.getId(), activeBatch);
        Long batchRunId = run.getId();
        runExecutor.submit(() -> executeRun(batchRunId, snapshot, activeBatch));
        return toRunResponse(run, items);
    }

    public HttpBatchRunResponse getRun(Long batchRunId) {
        HttpBatchRunEntity run = findRun(batchRunId);
        return toRunResponse(run, batchItemRepository.findByBatchRunIdOrderBySequenceNumberAsc(batchRunId));
    }

    public HttpBatchRunPageResponse listRuns(Long requestId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_TOTAL_COUNT) {
            throw badRequest("HTTP_BATCH_PAGE_INVALID", "批次結果分頁參數錯誤。");
        }
        PageRequest pageable = PageRequest.of(page, size);
        Page<HttpBatchRunEntity> result = requestId == null
                ? batchRunRepository.findAllByOrderByCreatedAtDesc(pageable)
                : batchRunRepository.findByRequestIdOrderByCreatedAtDesc(requestId, pageable);
        return new HttpBatchRunPageResponse(
                result.getContent().stream()
                        .map(run -> toRunResponse(run, batchItemRepository.findByBatchRunIdOrderBySequenceNumberAsc(run.getId())))
                        .toList(),
                page,
                size,
                result.getTotalElements()
        );
    }

    public HttpBatchItemPageResponse listItems(Long batchRunId, int page, int size) {
        findRun(batchRunId);
        if (page < 0 || size < 1 || size > MAX_TOTAL_COUNT) {
            throw badRequest("HTTP_BATCH_PAGE_INVALID", "批次結果分頁參數錯誤。");
        }
        Page<HttpBatchItemEntity> result = batchItemRepository.findByBatchRunIdOrderBySequenceNumberAsc(batchRunId, PageRequest.of(page, size));
        return new HttpBatchItemPageResponse(
                result.getContent().stream().map(this::toItemResponse).toList(),
                page,
                size,
                result.getTotalElements()
        );
    }

    public HttpBatchRunResponse cancel(Long batchRunId) {
        HttpBatchRunEntity run = findRun(batchRunId);
        if (run.getStatus() != HttpBatchRunStatus.RUNNING) {
            return toRunResponse(run, batchItemRepository.findByBatchRunIdOrderBySequenceNumberAsc(batchRunId));
        }

        ActiveBatch activeBatch = activeBatches.get(batchRunId);
        if (activeBatch != null) {
            activeBatch.stop(StopReason.CANCELLED, executionCancellationService);
        } else {
            run.setStatus(HttpBatchRunStatus.CANCELLED);
            run.setCompletedAt(Instant.now());
            batchRunRepository.save(run);
        }
        return getRun(batchRunId);
    }

    private void executeRun(Long batchRunId, HttpExecuteRequest snapshot, ActiveBatch activeBatch) {
        HttpBatchRunEntity run = findRun(batchRunId);
        run.setStartedAt(Instant.now());
        batchRunRepository.save(run);

        try {
            List<HttpBatchItemEntity> items = batchItemRepository.findByBatchRunIdOrderBySequenceNumberAsc(batchRunId);
            if (run.getMode() == HttpBatchMode.RESPONSE_INTERVAL) {
                executeWithResponseInterval(run, items, snapshot, activeBatch);
            } else {
                long deadlineNanos = run.getMode() == HttpBatchMode.DEADLINE
                        ? System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(run.getDeadlineMillis())
                        : Long.MAX_VALUE;
                executeWithConcurrency(run, items, snapshot, activeBatch, deadlineNanos);
            }
        } catch (RuntimeException exception) {
            activeBatch.stop(StopReason.CANCELLED, executionCancellationService);
        } finally {
            completeRun(batchRunId, activeBatch);
            activeBatches.remove(batchRunId, activeBatch);
        }
    }

    private void executeWithResponseInterval(
            HttpBatchRunEntity run,
            List<HttpBatchItemEntity> items,
            HttpExecuteRequest snapshot,
            ActiveBatch activeBatch
    ) {
        for (HttpBatchItemEntity item : items) {
            if (activeBatch.isStopped()) {
                break;
            }
            executeItem(run, item.getId(), snapshot, activeBatch, Long.MAX_VALUE);
            if (!activeBatch.isStopped() && item.getSequenceNumber() < run.getTotalCount()) {
                sleepUntilNext(activeBatch, run.getIntervalMillis());
            }
        }
    }

    private void executeWithConcurrency(
            HttpBatchRunEntity run,
            List<HttpBatchItemEntity> items,
            HttpExecuteRequest snapshot,
            ActiveBatch activeBatch,
            long deadlineNanos
    ) {
        ExecutorCompletionService<Void> completionService = new ExecutorCompletionService<>(itemExecutor);
        int nextIndex = 0;
        int inProgress = 0;
        int concurrency = run.getMaxConcurrency();

        while (nextIndex < items.size() && inProgress < concurrency && canStart(activeBatch, deadlineNanos)) {
            submitItem(completionService, run, items.get(nextIndex++), snapshot, activeBatch, deadlineNanos);
            inProgress++;
        }

        while (inProgress > 0) {
            Future<Void> completed = waitForCompletion(completionService, deadlineNanos, activeBatch);
            if (completed == null) {
                if (!activeBatch.isStopped()) {
                    activeBatch.stop(StopReason.DEADLINE, executionCancellationService);
                }
                break;
            }
            inProgress--;
            consumeCompletedFuture(completed);

            while (nextIndex < items.size() && inProgress < concurrency && canStart(activeBatch, deadlineNanos)) {
                submitItem(completionService, run, items.get(nextIndex++), snapshot, activeBatch, deadlineNanos);
                inProgress++;
            }
        }

        if (activeBatch.isStopped()) {
            activeBatch.cancelChildren(executionCancellationService);
        }
        while (inProgress > 0) {
            try {
                consumeCompletedFuture(completionService.take());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                activeBatch.stop(StopReason.CANCELLED, executionCancellationService);
                break;
            }
            inProgress--;
        }
    }

    private void submitItem(
            ExecutorCompletionService<Void> completionService,
            HttpBatchRunEntity run,
            HttpBatchItemEntity item,
            HttpExecuteRequest snapshot,
            ActiveBatch activeBatch,
            long deadlineNanos
    ) {
        completionService.submit(() -> {
            executeItem(run, item.getId(), snapshot, activeBatch, deadlineNanos);
            return null;
        });
    }

    private Future<Void> waitForCompletion(
            ExecutorCompletionService<Void> completionService,
            long deadlineNanos,
            ActiveBatch activeBatch
    ) {
        try {
            if (deadlineNanos == Long.MAX_VALUE) {
                return completionService.take();
            }
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                return null;
            }
            return completionService.poll(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            activeBatch.stop(StopReason.CANCELLED, executionCancellationService);
            return null;
        }
    }

    private void consumeCompletedFuture(Future<Void> completed) {
        try {
            completed.get();
        } catch (Exception ignored) {
            // Each item has already persisted its own execution outcome.
        }
    }

    private boolean canStart(ActiveBatch activeBatch, long deadlineNanos) {
        if (activeBatch.isStopped()) {
            return false;
        }
        if (deadlineNanos != Long.MAX_VALUE && System.nanoTime() >= deadlineNanos) {
            activeBatch.stop(StopReason.DEADLINE, executionCancellationService);
            return false;
        }
        return true;
    }

    private void executeItem(
            HttpBatchRunEntity run,
            Long itemId,
            HttpExecuteRequest snapshot,
            ActiveBatch activeBatch,
            long deadlineNanos
    ) {
        HttpBatchItemEntity item = batchItemRepository.findById(itemId).orElseThrow();
        if (!canStart(activeBatch, deadlineNanos)) {
            return;
        }

        item.setStatus(HttpBatchItemStatus.RUNNING);
        item.setStartedAt(Instant.now());
        batchItemRepository.save(item);

        String executionId = "http-batch-" + run.getId() + "-" + item.getSequenceNumber();
        ExecutionHandle execution = executionCancellationService.start(executionId);
        activeBatch.addChild(executionId);
        try {
            if (activeBatch.isStopped()) {
                executionCancellationService.cancel(executionId);
            }
            HttpExecuteResponse response = httpExecuteService.execute(
                    withBatchExecution(snapshot, executionId, remainingTimeout(snapshot.timeoutMillis(), deadlineNanos)),
                    execution,
                    false
            );
            item.setStatus(response.statusCode() >= 400 ? HttpBatchItemStatus.FAILED : HttpBatchItemStatus.SUCCESS);
            item.setStatusCode(response.statusCode());
            item.setReasonPhrase(response.reasonPhrase());
            item.setDurationMillis(response.durationMillis());
            item.setSizeBytes(response.sizeBytes());
            item.setResponseHeadersJson(writeJson(response.headers()));
            item.setResponseBodyPreview(truncate(response.body(), RESPONSE_BODY_PREVIEW_LIMIT));
        } catch (ApiException exception) {
            if (deadlineReachedOrImminent(deadlineNanos)) {
                activeBatch.stop(StopReason.DEADLINE, executionCancellationService);
            }
            boolean cancelled = execution.isCancelled() || activeBatch.isStopped()
                    || "HTTP_REQUEST_CANCELLED".equals(exception.getCode());
            item.setStatus(cancelled ? HttpBatchItemStatus.CANCELLED : HttpBatchItemStatus.FAILED);
            item.setErrorMessage(truncate(resolveErrorMessage(exception), ERROR_MESSAGE_LIMIT));
        } catch (RuntimeException exception) {
            item.setStatus(activeBatch.isStopped() ? HttpBatchItemStatus.CANCELLED : HttpBatchItemStatus.FAILED);
            item.setErrorMessage(truncate(resolveErrorMessage(exception), ERROR_MESSAGE_LIMIT));
        } finally {
            item.setCompletedAt(Instant.now());
            batchItemRepository.save(item);
            activeBatch.removeChild(executionId);
            executionCancellationService.finish(execution);
        }
    }

    private void sleepUntilNext(ActiveBatch activeBatch, int intervalMillis) {
        long endNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(intervalMillis);
        while (!activeBatch.isStopped()) {
            long remainingNanos = endNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                return;
            }
            try {
                TimeUnit.NANOSECONDS.sleep(Math.min(remainingNanos, TimeUnit.MILLISECONDS.toNanos(100)));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                activeBatch.stop(StopReason.CANCELLED, executionCancellationService);
                return;
            }
        }
    }

    private void completeRun(Long batchRunId, ActiveBatch activeBatch) {
        HttpBatchRunEntity run = findRun(batchRunId);
        if (run.getStatus() != HttpBatchRunStatus.RUNNING) {
            return;
        }
        run.setStatus(switch (activeBatch.stopReason()) {
            case DEADLINE -> HttpBatchRunStatus.DEADLINE_EXCEEDED;
            case CANCELLED -> HttpBatchRunStatus.CANCELLED;
            case NONE -> HttpBatchRunStatus.COMPLETED;
        });
        run.setCompletedAt(Instant.now());
        batchRunRepository.save(run);
    }

    private NormalizedBatchConfig normalizeStartRequest(HttpBatchStartRequest request) {
        if (request == null || request.httpRequest() == null) {
            throw badRequest("HTTP_BATCH_REQUEST_REQUIRED", "批次執行需要 HTTP 請求內容。");
        }
        if (request.mode() == null) {
            throw badRequest("HTTP_BATCH_MODE_REQUIRED", "請選擇批次執行模式。");
        }
        if (request.totalCount() == null || request.totalCount() < 1 || request.totalCount() > MAX_TOTAL_COUNT) {
            throw badRequest("HTTP_BATCH_TOTAL_COUNT_INVALID", "批次總筆數必須介於 1 到 100。 ");
        }

        int maxConcurrency = request.maxConcurrency() == null ? DEFAULT_MAX_CONCURRENCY : request.maxConcurrency();
        if (maxConcurrency < 1 || maxConcurrency > MAX_TOTAL_COUNT) {
            throw badRequest("HTTP_BATCH_CONCURRENCY_INVALID", "最大併發數必須介於 1 到 100。 ");
        }
        if (request.mode() == HttpBatchMode.RESPONSE_INTERVAL) {
            if (request.intervalMillis() == null || request.intervalMillis() < 0 || request.intervalMillis() > MAX_INTERVAL_MILLIS) {
                throw badRequest("HTTP_BATCH_INTERVAL_INVALID", "回應後間隔必須介於 0 到 300000 毫秒。 ");
            }
            return new NormalizedBatchConfig(request.httpRequest(), request.mode(), request.totalCount(), 1, request.intervalMillis(), null);
        }
        if (request.mode() == HttpBatchMode.DEADLINE) {
            if (request.deadlineMillis() == null || request.deadlineMillis() < MIN_DEADLINE_MILLIS || request.deadlineMillis() > MAX_DEADLINE_MILLIS) {
                throw badRequest("HTTP_BATCH_DEADLINE_INVALID", "完成期限必須介於 1000 到 3600000 毫秒。 ");
            }
            return new NormalizedBatchConfig(request.httpRequest(), request.mode(), request.totalCount(), maxConcurrency, null, request.deadlineMillis());
        }
        return new NormalizedBatchConfig(request.httpRequest(), request.mode(), request.totalCount(), maxConcurrency, null, null);
    }

    private HttpExecuteRequest withoutExecutionId(HttpExecuteRequest request) {
        return new HttpExecuteRequest(
                null, request.requestId(), request.method(), request.url(), request.params(), request.headers(),
                request.bodyType(), request.body(), request.formData(), request.timeoutMillis(),
                request.followRedirects(), request.ignoreSslVerification()
        );
    }

    private HttpExecuteRequest withBatchExecution(HttpExecuteRequest request, String executionId, int timeoutMillis) {
        return new HttpExecuteRequest(
                executionId, request.requestId(), request.method(), request.url(), request.params(), request.headers(),
                request.bodyType(), request.body(), request.formData(), timeoutMillis,
                request.followRedirects(), request.ignoreSslVerification()
        );
    }

    private int remainingTimeout(Integer configuredTimeoutMillis, long deadlineNanos) {
        int configuredTimeout = configuredTimeoutMillis == null ? DEFAULT_HTTP_TIMEOUT_MILLIS : configuredTimeoutMillis;
        if (deadlineNanos == Long.MAX_VALUE) {
            return configuredTimeout;
        }
        long remainingNanos = Math.max(1, deadlineNanos - System.nanoTime());
        long remainingMillis = (remainingNanos + TimeUnit.MILLISECONDS.toNanos(1) - 1) / TimeUnit.MILLISECONDS.toNanos(1);
        return (int) Math.max(1, Math.min(configuredTimeout, remainingMillis));
    }

    private boolean deadlineReachedOrImminent(long deadlineNanos) {
        if (deadlineNanos == Long.MAX_VALUE) {
            return false;
        }
        return deadlineNanos - System.nanoTime() <= TimeUnit.MILLISECONDS.toNanos(5);
    }

    private HttpBatchRunEntity findRun(Long batchRunId) {
        return batchRunRepository.findById(batchRunId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "HTTP_BATCH_NOT_FOUND", "找不到指定的 HTTP 批次執行。"));
    }

    private HttpBatchRunResponse toRunResponse(HttpBatchRunEntity run, List<HttpBatchItemEntity> items) {
        int inProgress = count(items, HttpBatchItemStatus.RUNNING);
        int success = count(items, HttpBatchItemStatus.SUCCESS);
        int failed = count(items, HttpBatchItemStatus.FAILED);
        int cancelled = count(items, HttpBatchItemStatus.CANCELLED);
        int notDispatched = count(items, HttpBatchItemStatus.NOT_DISPATCHED);
        List<Long> durations = items.stream()
                .map(HttpBatchItemEntity::getDurationMillis)
                .filter(duration -> duration != null)
                .toList();
        Long average = durations.isEmpty() ? null : Math.round(durations.stream().mapToLong(Long::longValue).average().orElse(0));
        Long fastest = durations.isEmpty() ? null : durations.stream().mapToLong(Long::longValue).min().orElseThrow();
        Long slowest = durations.isEmpty() ? null : durations.stream().mapToLong(Long::longValue).max().orElseThrow();
        Long duration = run.getStartedAt() == null ? null
                : (run.getCompletedAt() == null ? Instant.now() : run.getCompletedAt()).toEpochMilli() - run.getStartedAt().toEpochMilli();
        return new HttpBatchRunResponse(
                run.getId(), run.getRequestId(), run.getMode(), run.getStatus(), run.getTotalCount(), run.getMaxConcurrency(),
                run.getIntervalMillis(), run.getDeadlineMillis(), run.getTotalCount() - notDispatched, inProgress, success, failed,
                cancelled, notDispatched, duration, average, fastest, slowest, run.getCreatedAt(), run.getStartedAt(), run.getCompletedAt()
        );
    }

    private int count(List<HttpBatchItemEntity> items, HttpBatchItemStatus status) {
        return (int) items.stream().filter(item -> item.getStatus() == status).count();
    }

    private HttpBatchItemResponse toItemResponse(HttpBatchItemEntity item) {
        return new HttpBatchItemResponse(
                item.getId(), item.getSequenceNumber(), item.getStatus(), item.getStatusCode(), item.getReasonPhrase(),
                item.getDurationMillis(), item.getSizeBytes(), item.getErrorMessage(), readHeaders(item.getResponseHeadersJson()),
                item.getResponseBodyPreview(), item.getStartedAt(), item.getCompletedAt()
        );
    }

    private List<HttpNameValue> readHeaders(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<HttpNameValue>>() { });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "HTTP_BATCH_SERIALIZE_FAILED", "批次執行資料序列化失敗。", Map.of());
        }
    }

    private String resolveErrorMessage(Exception exception) {
        if (exception instanceof ApiException apiException) {
            Object reason = apiException.getDetails().get("reason");
            if (reason != null) {
                return String.valueOf(reason);
            }
        }
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n...[內容已截斷]";
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    @PreDestroy
    void shutdownExecutors() {
        runExecutor.shutdownNow();
        itemExecutor.shutdownNow();
    }

    private record NormalizedBatchConfig(
            HttpExecuteRequest httpRequest,
            HttpBatchMode mode,
            int totalCount,
            int maxConcurrency,
            Integer intervalMillis,
            Integer deadlineMillis
    ) {
    }

    private enum StopReason {
        NONE,
        CANCELLED,
        DEADLINE
    }

    private static final class ActiveBatch {

        private final AtomicReference<StopReason> stopReason = new AtomicReference<>(StopReason.NONE);
        private final Set<String> childExecutionIds = ConcurrentHashMap.newKeySet();

        void addChild(String executionId) {
            childExecutionIds.add(executionId);
        }

        void removeChild(String executionId) {
            childExecutionIds.remove(executionId);
        }

        boolean isStopped() {
            return stopReason.get() != StopReason.NONE;
        }

        StopReason stopReason() {
            return stopReason.get();
        }

        void stop(StopReason reason, ExecutionCancellationService cancellationService) {
            stopReason.compareAndSet(StopReason.NONE, reason);
            cancelChildren(cancellationService);
        }

        void cancelChildren(ExecutionCancellationService cancellationService) {
            for (String executionId : childExecutionIds) {
                cancellationService.cancel(executionId);
            }
        }
    }
}
