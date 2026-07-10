package com.postbubi.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.postbubi.grpcbur.GrpcBurExecuteService;
import com.postbubi.web.dto.GrpcBurExecuteRequest;
import com.postbubi.web.dto.GrpcBurExecuteResponse;

@RestController
@RequestMapping("/api/grpc-bur")
public class GrpcBurExecuteController {

    private final GrpcBurExecuteService grpcBurExecuteService;

    public GrpcBurExecuteController(GrpcBurExecuteService grpcBurExecuteService) {
        this.grpcBurExecuteService = grpcBurExecuteService;
    }

    @PostMapping("/execute")
    public GrpcBurExecuteResponse execute(@RequestBody GrpcBurExecuteRequest request) {
        return grpcBurExecuteService.execute(request);
    }

    @PostMapping("/preview")
    public GrpcBurExecuteResponse.GrpcBurRequestPreview preview(@RequestBody GrpcBurExecuteRequest request) {
        return grpcBurExecuteService.preview(request);
    }
}
