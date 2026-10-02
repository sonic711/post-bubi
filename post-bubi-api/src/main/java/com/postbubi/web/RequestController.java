package com.postbubi.web;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

import com.postbubi.service.WorkspaceService;
import com.postbubi.workspace.WorkspaceArchiveService;
import com.postbubi.web.dto.RequestCreateRequest;
import com.postbubi.web.dto.RequestResponse;
import com.postbubi.web.dto.RequestUpdateRequest;

@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final WorkspaceService workspaceService;
    private final WorkspaceArchiveService workspaceArchiveService;

    public RequestController(WorkspaceService workspaceService, WorkspaceArchiveService workspaceArchiveService) {
        this.workspaceService = workspaceService;
        this.workspaceArchiveService = workspaceArchiveService;
    }

    @GetMapping("/{id}")
    public RequestResponse getRequest(@PathVariable Long id) {
        return workspaceService.getRequest(id);
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportRequest(@PathVariable Long id) {
        byte[] content = workspaceArchiveService.exportRequest(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("post-bubi-request.zip")
                        .build()
                        .toString())
                .body(content);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RequestResponse createRequest(@RequestBody RequestCreateRequest request) {
        return workspaceService.createRequest(request);
    }

    @PutMapping("/{id}")
    public RequestResponse updateRequest(@PathVariable Long id, @RequestBody RequestUpdateRequest request) {
        return workspaceService.updateRequest(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRequest(@PathVariable Long id) {
        workspaceService.deleteRequest(id);
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestResponse duplicateRequest(@PathVariable Long id) {
        return workspaceService.duplicateRequest(id);
    }
}
