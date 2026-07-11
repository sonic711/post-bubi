package com.postbubi.web;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.postbubi.execution.ExecutionCancellationService;

@RestController
@RequestMapping("/api/executions")
public class ExecutionController {

    private final ExecutionCancellationService executionCancellationService;

    public ExecutionController(ExecutionCancellationService executionCancellationService) {
        this.executionCancellationService = executionCancellationService;
    }

    @PostMapping("/{executionId}/cancel")
    public ExecutionCancelResponse cancel(@PathVariable String executionId) {
        return new ExecutionCancelResponse(executionId, executionCancellationService.cancel(executionId));
    }

    public record ExecutionCancelResponse(String executionId, boolean cancelled) {
    }
}
