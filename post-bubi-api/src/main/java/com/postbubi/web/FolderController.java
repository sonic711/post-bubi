package com.postbubi.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.postbubi.service.WorkspaceService;
import com.postbubi.web.dto.FolderCreateRequest;
import com.postbubi.web.dto.FolderResponse;
import com.postbubi.web.dto.FolderUpdateRequest;

@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final WorkspaceService workspaceService;

    public FolderController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FolderResponse createFolder(@RequestBody FolderCreateRequest request) {
        return workspaceService.createFolder(request);
    }

    @PutMapping("/{id}")
    public FolderResponse updateFolder(@PathVariable Long id, @RequestBody FolderUpdateRequest request) {
        return workspaceService.updateFolder(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFolder(@PathVariable Long id) {
        workspaceService.deleteFolder(id);
    }
}
