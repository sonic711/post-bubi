package com.postbubi.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.postbubi.grpc.GrpcExecuteService;
import com.postbubi.web.dto.GrpcExecuteRequest;
import com.postbubi.web.dto.GrpcExecuteResponse;

@RestController
@RequestMapping("/api/grpc")
public class GrpcExecuteController {

    private final GrpcExecuteService grpcExecuteService;

    public GrpcExecuteController(GrpcExecuteService grpcExecuteService) {
        this.grpcExecuteService = grpcExecuteService;
    }

    @PostMapping("/execute")
    public GrpcExecuteResponse execute(@RequestBody GrpcExecuteRequest request) {
        return grpcExecuteService.execute(request);
    }
}
