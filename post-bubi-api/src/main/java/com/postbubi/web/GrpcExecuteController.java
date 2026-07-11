package com.postbubi.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.postbubi.execution.ExecutionCancellationService;
import com.postbubi.execution.ExecutionCancellationService.ExecutionHandle;
import com.postbubi.grpc.GrpcExecuteService;
import com.postbubi.web.dto.GrpcExecuteRequest;
import com.postbubi.web.dto.GrpcExecuteResponse;

@RestController
@RequestMapping("/api/grpc")
public class GrpcExecuteController {

    private final GrpcExecuteService grpcExecuteService;
    private final ExecutionCancellationService executionCancellationService;

    public GrpcExecuteController(
            GrpcExecuteService grpcExecuteService,
            ExecutionCancellationService executionCancellationService
    ) {
        this.grpcExecuteService = grpcExecuteService;
        this.executionCancellationService = executionCancellationService;
    }

    @PostMapping("/execute")
    public GrpcExecuteResponse execute(@RequestBody GrpcExecuteRequest request) {
        ExecutionHandle execution = executionCancellationService.start(request.executionId());
        try {
            return grpcExecuteService.execute(request, execution);
        } finally {
            executionCancellationService.finish(execution);
        }
    }
}
