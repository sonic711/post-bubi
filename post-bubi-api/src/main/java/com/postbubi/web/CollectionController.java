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

import com.postbubi.service.WorkspaceService;
import com.postbubi.workspace.WorkspaceArchiveService;
import com.postbubi.web.dto.CollectionCreateRequest;
import com.postbubi.web.dto.CollectionResponse;
import com.postbubi.web.dto.CollectionUpdateRequest;

@RestController
@RequestMapping("/api/collections")
public class CollectionController {

    private final WorkspaceService workspaceService;
    private final WorkspaceArchiveService workspaceArchiveService;

    public CollectionController(WorkspaceService workspaceService, WorkspaceArchiveService workspaceArchiveService) {
        this.workspaceService = workspaceService;
        this.workspaceArchiveService = workspaceArchiveService;
    }

    @GetMapping
    public List<CollectionResponse> listCollections() {
        return workspaceService.listCollections();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionResponse createCollection(@RequestBody CollectionCreateRequest request) {
        return workspaceService.createCollection(request);
    }

    @PutMapping("/{id}")
    public CollectionResponse updateCollection(@PathVariable Long id, @RequestBody CollectionUpdateRequest request) {
        return workspaceService.updateCollection(id, request);
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportCollection(@PathVariable Long id) {
        byte[] content = workspaceArchiveService.exportCollection(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("post-bubi-collection.zip")
                        .build()
                        .toString())
                .body(content);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCollection(@PathVariable Long id) {
        workspaceService.deleteCollection(id);
    }
}
