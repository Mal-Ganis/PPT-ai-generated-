package com.example.pptbackend.config;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * 统一 JSON 错误体 {@code {"message":"..."}}，与前端 axios 解析一致；区分配置类错误与上游大模型错误。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(EntityNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> typeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName() != null ? ex.getName() : "参数";
        String msg;
        if ("projectId".equals(name)) {
            msg = "项目 ID 须为数字。创建主题请使用 POST /api/projects/topic，请求体为 JSON：{\"topic\":\"你的主题\"}；勿在浏览器地址栏直接打开该地址。";
        } else if ("slideId".equals(name)) {
            msg = "幻灯片 ID 须为数字。";
        } else {
            msg = "参数「" + name + "」类型不正确。";
        }
        return ResponseEntity.badRequest().body(Map.of("message", msg));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> notReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
            .body(Map.of("message", "请求体须为合法 JSON，例如创建主题：{\"topic\":\"演示主题\"}。"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> illegalState(IllegalStateException exception) {
        String msg = exception.getMessage() != null ? exception.getMessage() : "服务暂时不可用";
        String lower = msg.toLowerCase();
        boolean configOrInput = (msg.contains("缺少") && (msg.contains("密钥") || msg.contains("API")))
            || msg.contains("未配置")
            || lower.contains("not configured");
        if (configOrInput) {
            return ResponseEntity.badRequest().body(Map.of("message", msg));
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", msg));
    }
}
