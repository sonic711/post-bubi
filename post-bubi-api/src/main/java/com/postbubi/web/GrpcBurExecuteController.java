package com.postbubi.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.postbubi.execution.ExecutionCancellationService;
import com.postbubi.execution.ExecutionCancellationService.ExecutionHandle;
import com.postbubi.grpcbur.GrpcBurExecuteService;
import com.postbubi.web.dto.GrpcBurExecuteRequest;
import com.postbubi.web.dto.GrpcBurExecuteResponse;

@RestController
@RequestMapping("/api/grpc-bur")
public class GrpcBurExecuteController {

    private final GrpcBurExecuteService grpcBurExecuteService;
    private final ExecutionCancellationService executionCancellationService;

    public GrpcBurExecuteController(
            GrpcBurExecuteService grpcBurExecuteService,
            ExecutionCancellationService executionCancellationService
    ) {
        this.grpcBurExecuteService = grpcBurExecuteService;
        this.executionCancellationService = executionCancellationService;
    }

    @PostMapping("/execute")
    public GrpcBurExecuteResponse execute(@RequestBody GrpcBurExecuteRequest request) {
        ExecutionHandle execution = executionCancellationService.start(request.executionId());
        try {
            return grpcBurExecuteService.execute(request, execution);
        } finally {
            executionCancellationService.finish(execution);
        }
    }

    @PostMapping("/preview")
    public GrpcBurExecuteResponse.GrpcBurRequestPreview preview(@RequestBody GrpcBurExecuteRequest request) {
        return grpcBurExecuteService.preview(request);
    }
}
