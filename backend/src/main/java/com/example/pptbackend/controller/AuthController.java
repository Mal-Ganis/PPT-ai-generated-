package com.example.pptbackend.controller;

import com.example.pptbackend.dto.AuthResponse;
import com.example.pptbackend.dto.EditorAccessStatusDto;
import com.example.pptbackend.dto.LoginRequest;
import com.example.pptbackend.dto.RegisterRequest;
import com.example.pptbackend.dto.SubmitEditorAccessRequest;
import com.example.pptbackend.dto.UserProfileDto;
import com.example.pptbackend.service.AuthService;
import com.example.pptbackend.service.EditorAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EditorAccessService editorAccessService;

    public AuthController(AuthService authService, EditorAccessService editorAccessService) {
        this.authService = authService;
        this.editorAccessService = editorAccessService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> me() {
        return ResponseEntity.ok(authService.me());
    }

    @PostMapping("/editor-access-request")
    public ResponseEntity<EditorAccessStatusDto> submitEditorAccessRequest(
        @RequestBody(required = false) SubmitEditorAccessRequest request
    ) {
        return ResponseEntity.ok(editorAccessService.submitRequest(request));
    }

    @GetMapping("/editor-access-request/status")
    public ResponseEntity<EditorAccessStatusDto> editorAccessStatus() {
        return ResponseEntity.ok(editorAccessService.myStatus());
    }
}
