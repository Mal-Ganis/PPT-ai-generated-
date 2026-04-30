package com.example.pptbackend.controller;

import com.example.pptbackend.dto.ExternalSourceDocument;
import com.example.pptbackend.dto.ExternalSourceLoadRequest;
import com.example.pptbackend.dto.ExternalSourceLoadResponse;
import com.example.pptbackend.service.ExternalKnowledgeSourceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/external-sources")
public class ExternalSourceController {

    private final ExternalKnowledgeSourceService externalKnowledgeSourceService;

    public ExternalSourceController(ExternalKnowledgeSourceService externalKnowledgeSourceService) {
        this.externalKnowledgeSourceService = externalKnowledgeSourceService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<ExternalSourceDocument>> search(
        @RequestParam String query,
        @RequestParam(defaultValue = "3") int limit
    ) {
        return ResponseEntity.ok(externalKnowledgeSourceService.searchExternalSources(query, limit));
    }

    @PostMapping("/load")
    public ResponseEntity<ExternalSourceLoadResponse> load(@RequestBody ExternalSourceLoadRequest request) {
        int loadedCount = externalKnowledgeSourceService.loadExternalSources(request);
        return ResponseEntity.ok(new ExternalSourceLoadResponse(loadedCount));
    }
}
