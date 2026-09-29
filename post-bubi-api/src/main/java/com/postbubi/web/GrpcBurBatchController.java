package com.postbubi.web;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.postbubi.domain.GrpcBatchProtocol;
import com.postbubi.grpc.GrpcBatchService;
import com.postbubi.web.dto.GrpcBatchClearResponse;
import com.postbubi.web.dto.GrpcBatchItemPageResponse;
import com.postbubi.web.dto.GrpcBatchRunPageResponse;
import com.postbubi.web.dto.GrpcBatchRunResponse;
import com.postbubi.web.dto.GrpcBurBatchStartRequest;

@RestController
@RequestMapping("/api/grpc-bur/batch-runs")
public class GrpcBurBatchController {
    private final GrpcBatchService service;
    public GrpcBurBatchController(GrpcBatchService service) { this.service = service; }

    @PostMapping public GrpcBatchRunResponse start(@RequestBody GrpcBurBatchStartRequest request) { return service.startGrpcBur(request); }
    @GetMapping public GrpcBatchRunPageResponse list(@RequestParam(required = false) Long requestId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return service.listRuns(GrpcBatchProtocol.GRPC_BUR, requestId, page, size); }
    @DeleteMapping public GrpcBatchClearResponse clear(@RequestParam Long requestId) { return service.clearCompletedRuns(GrpcBatchProtocol.GRPC_BUR, requestId); }
    @GetMapping("/{batchRunId}") public GrpcBatchRunResponse get(@PathVariable Long batchRunId) { return service.getRun(GrpcBatchProtocol.GRPC_BUR, batchRunId); }
    @GetMapping("/{batchRunId}/items") public GrpcBatchItemPageResponse items(@PathVariable Long batchRunId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return service.listItems(GrpcBatchProtocol.GRPC_BUR, batchRunId, page, size); }
    @PostMapping("/{batchRunId}/cancel") public GrpcBatchRunResponse cancel(@PathVariable Long batchRunId) { return service.cancel(GrpcBatchProtocol.GRPC_BUR, batchRunId); }
    @GetMapping("/{batchRunId}/export.csv") public ResponseEntity<byte[]> csv(@PathVariable Long batchRunId) { return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("post-bubi-grpc-bur-batch-" + batchRunId + ".csv").build().toString()).body(service.exportCsv(GrpcBatchProtocol.GRPC_BUR, batchRunId)); }
}
