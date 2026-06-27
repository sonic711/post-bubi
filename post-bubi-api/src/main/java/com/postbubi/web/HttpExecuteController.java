package com.postbubi.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.postbubi.http.HttpExecuteService;
import com.postbubi.web.dto.HttpExecuteRequest;
import com.postbubi.web.dto.HttpExecuteResponse;

@RestController
@RequestMapping("/api/http")
public class HttpExecuteController {

    private final HttpExecuteService httpExecuteService;

    public HttpExecuteController(HttpExecuteService httpExecuteService) {
        this.httpExecuteService = httpExecuteService;
    }

    @PostMapping("/execute")
    public HttpExecuteResponse execute(@RequestBody HttpExecuteRequest request) {
        return httpExecuteService.execute(request);
    }
}
