package com.postbubi.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.postbubi.execution.ExecutionCancellationService;
import com.postbubi.execution.ExecutionCancellationService.ExecutionHandle;
import com.postbubi.http.HttpExecuteService;
import com.postbubi.http.RequestHistoryService;
import com.postbubi.web.dto.HttpExecuteRequest;
import com.postbubi.web.dto.HttpExecuteResponse;
import com.postbubi.web.dto.RequestHistoryResponse;

import java.util.List;

@RestController
@RequestMapping("/api/http")
public class HttpExecuteController {

    private final HttpExecuteService httpExecuteService;
    private final RequestHistoryService requestHistoryService;
    private final ExecutionCancellationService executionCancellationService;

    public HttpExecuteController(
            HttpExecuteService httpExecuteService,
            RequestHistoryService requestHistoryService,
            ExecutionCancellationService executionCancellationService
    ) {
        this.httpExecuteService = httpExecuteService;
        this.requestHistoryService = requestHistoryService;
        this.executionCancellationService = executionCancellationService;
    }

    @PostMapping("/execute")
    public HttpExecuteResponse execute(@RequestBody HttpExecuteRequest request) {
        ExecutionHandle execution = executionCancellationService.start(request.executionId());
        try {
            return httpExecuteService.execute(request, execution);
        } finally {
            executionCancellationService.finish(execution);
        }
    }

    @GetMapping("/history")
    public List<RequestHistoryResponse> history() {
        return requestHistoryService.listRecent();
    }
}
