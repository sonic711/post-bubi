package com.postbubi.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.postbubi.http.HttpBatchService;
import com.postbubi.web.dto.HttpBatchItemPageResponse;
import com.postbubi.web.dto.HttpBatchRunResponse;
import com.postbubi.web.dto.HttpBatchRunPageResponse;
import com.postbubi.web.dto.HttpBatchStartRequest;

@RestController
@RequestMapping("/api/http/batch-runs")
public class HttpBatchController {

    private final HttpBatchService httpBatchService;

    public HttpBatchController(HttpBatchService httpBatchService) {
        this.httpBatchService = httpBatchService;
    }

    @PostMapping
    public HttpBatchRunResponse start(@RequestBody HttpBatchStartRequest request) {
        return httpBatchService.start(request);
    }

    @GetMapping
    public HttpBatchRunPageResponse listRuns(
            @RequestParam(required = false) Long requestId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return httpBatchService.listRuns(requestId, page, size);
    }

    @GetMapping("/{batchRunId}")
    public HttpBatchRunResponse getRun(@PathVariable Long batchRunId) {
        return httpBatchService.getRun(batchRunId);
    }

    @GetMapping("/{batchRunId}/items")
    public HttpBatchItemPageResponse listItems(
            @PathVariable Long batchRunId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return httpBatchService.listItems(batchRunId, page, size);
    }

    @PostMapping("/{batchRunId}/cancel")
    public HttpBatchRunResponse cancel(@PathVariable Long batchRunId) {
        return httpBatchService.cancel(batchRunId);
    }
}
