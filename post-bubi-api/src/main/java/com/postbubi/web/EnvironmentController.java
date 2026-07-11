package com.postbubi.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.postbubi.service.EnvironmentService;
import com.postbubi.web.dto.EnvironmentCreateRequest;
import com.postbubi.web.dto.EnvironmentResponse;
import com.postbubi.web.dto.EnvironmentUpdateRequest;

@RestController
@RequestMapping("/api/environments")
public class EnvironmentController {

    private final EnvironmentService environmentService;

    public EnvironmentController(EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    @GetMapping
    public List<EnvironmentResponse> list() {
        return environmentService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnvironmentResponse create(@RequestBody EnvironmentCreateRequest request) {
        return environmentService.create(request);
    }

    @PutMapping("/{id}")
    public EnvironmentResponse update(@PathVariable Long id, @RequestBody EnvironmentUpdateRequest request) {
        return environmentService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        environmentService.delete(id);
    }
}
