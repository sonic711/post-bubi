package com.postbubi.web;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.postbubi.workspace.WorkspaceArchiveService;
import com.postbubi.workspace.WorkspaceArchiveService.ImportResult;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceArchiveController {

    private final WorkspaceArchiveService workspaceArchiveService;

    public WorkspaceArchiveController(WorkspaceArchiveService workspaceArchiveService) {
        this.workspaceArchiveService = workspaceArchiveService;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportWorkspace() {
        byte[] content = workspaceArchiveService.exportWorkspace();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("post-bubi-workspace.zip")
                        .build()
                        .toString())
                .body(content);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importWorkspace(@RequestParam("file") MultipartFile file) {
        return workspaceArchiveService.importWorkspace(file);
    }
}
