package com.postbubi.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.postbubi.proto.ProtoStorageService;
import com.postbubi.web.dto.ProtoInspectResponse;
import com.postbubi.web.dto.ProtoListResponse;
import com.postbubi.web.dto.ProtoUploadResponse;

@RestController
@RequestMapping("/api/protos")
public class ProtoController {

    private final ProtoStorageService protoStorageService;

    public ProtoController(ProtoStorageService protoStorageService) {
        this.protoStorageService = protoStorageService;
    }

    @GetMapping
    public List<ProtoListResponse> list() {
        return protoStorageService.list();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProtoUploadResponse upload(@RequestParam("file") MultipartFile file) {
        return protoStorageService.store(file);
    }

    @GetMapping("/{protoId}/inspect")
    public ProtoInspectResponse inspect(@PathVariable String protoId) {
        return protoStorageService.inspect(protoId);
    }
}
