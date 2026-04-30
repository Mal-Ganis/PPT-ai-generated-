package com.example.pptbackend.controller;

import com.example.pptbackend.dto.IndexSegmentRequest;
import com.example.pptbackend.dto.SearchRequest;
import com.example.pptbackend.dto.SearchResponse;
import com.example.pptbackend.service.IndexSegmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/index")
public class IndexSegmentController {

    private final IndexSegmentService indexSegmentService;

    public IndexSegmentController(IndexSegmentService indexSegmentService) {
        this.indexSegmentService = indexSegmentService;
    }

    @PostMapping("/segments")
    public ResponseEntity<Long> createSegment(@RequestBody IndexSegmentRequest request) {
        Long segmentId = indexSegmentService.indexSegment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(segmentId);
    }

    @PostMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestBody SearchRequest request) {
        SearchResponse response = indexSegmentService.search(request);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }
}
