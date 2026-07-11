package com.postbubi.execution;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.postbubi.web.error.ApiException;

@Service
public class ExecutionCancellationService {

    private static final int MAX_EXECUTION_ID_LENGTH = 120;

    private final ConcurrentMap<String, ExecutionHandle> activeExecutions = new ConcurrentHashMap<>();

    public ExecutionHandle start(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return ExecutionHandle.noop();
        }

        String normalizedId = executionId.trim();
        if (normalizedId.length() > MAX_EXECUTION_ID_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EXECUTION_ID_INVALID", "執行識別碼格式錯誤。");
        }

        ExecutionHandle handle = new ExecutionHandle(normalizedId);
        if (activeExecutions.putIfAbsent(normalizedId, handle) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "EXECUTION_ID_IN_USE", "相同執行識別碼的請求仍在進行中。");
        }
        return handle;
    }

    public void finish(ExecutionHandle handle) {
        if (handle == null || !handle.isManaged()) {
            return;
        }
        activeExecutions.remove(handle.executionId(), handle);
        handle.clearCancellationAction();
    }

    public boolean cancel(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return false;
        }
        ExecutionHandle handle = activeExecutions.get(executionId.trim());
        if (handle == null) {
            return false;
        }
        handle.cancel();
        return true;
    }

    public static final class ExecutionHandle {

        private static final ExecutionHandle NOOP = new ExecutionHandle(null);

        private final String executionId;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicReference<Runnable> cancellationAction = new AtomicReference<>();

        private ExecutionHandle(String executionId) {
            this.executionId = executionId;
        }

        private static ExecutionHandle noop() {
            return NOOP;
        }

        public String executionId() {
            return executionId;
        }

        public boolean isManaged() {
            return executionId != null;
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        public void registerCancellationAction(Runnable action) {
            if (!isManaged()) {
                return;
            }
            cancellationAction.set(action);
            if (isCancelled()) {
                runCancellationAction(action);
            }
        }

        public void clearCancellationAction() {
            cancellationAction.set(null);
        }

        private void cancel() {
            cancelled.set(true);
            runCancellationAction(cancellationAction.get());
        }

        private void runCancellationAction(Runnable action) {
            if (action == null) {
                return;
            }
            try {
                action.run();
            } catch (RuntimeException ignored) {
                // The execution thread will expose the original cancellation result.
            }
        }
    }
}
