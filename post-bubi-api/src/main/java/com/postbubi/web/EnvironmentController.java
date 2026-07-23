package com.postbubi.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.postbubi.service.EnvironmentService;
import com.postbubi.web.dto.EnvironmentCreateRequest;
import com.postbubi.web.dto.EnvironmentCopyRequest;
import com.postbubi.web.dto.EnvironmentResponse;
import com.postbubi.web.dto.EnvironmentUpdateRequest;
import com.postbubi.workspace.EnvironmentArchiveService;

@RestController
@RequestMapping("/api/environments")
public class EnvironmentController {

    private final EnvironmentService environmentService;
    private final EnvironmentArchiveService environmentArchiveService;

    public EnvironmentController(EnvironmentService environmentService, EnvironmentArchiveService environmentArchiveService) {
        this.environmentService = environmentService;
        this.environmentArchiveService = environmentArchiveService;
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

    @PostMapping("/{id}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public EnvironmentResponse copy(@PathVariable Long id, @RequestBody EnvironmentCopyRequest request) {
        return environmentService.copy(id, request);
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        byte[] content = environmentArchiveService.exportEnvironment(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("post-bubi-environment.zip")
                        .build()
                        .toString())
                .body(content);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public EnvironmentResponse importEnvironment(@RequestParam("file") MultipartFile file) {
        return environmentArchiveService.importEnvironment(file);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        environmentService.delete(id);
    }
}
