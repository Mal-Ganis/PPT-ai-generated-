package com.example.pptbackend.controller;

import com.example.pptbackend.dto.SystemConfigDto;
import com.example.pptbackend.service.SystemConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    public SystemConfigController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @GetMapping
    public ResponseEntity<SystemConfigDto> getConfig() {
        return ResponseEntity.ok(systemConfigService.getSystemConfig());
    }

    @PutMapping
    public ResponseEntity<SystemConfigDto> updateConfig(@RequestBody SystemConfigDto dto) {
        return ResponseEntity.ok(systemConfigService.saveSystemConfig(dto));
    }

    /** 重置为代码内置默认配置（LLM 参数与两份 Prompt 模板），并持久化。 */
    @PostMapping("/reset-defaults")
    public ResponseEntity<SystemConfigDto> resetDefaults() {
        return ResponseEntity.ok(systemConfigService.resetToBuiltInDefaults());
    }
}
