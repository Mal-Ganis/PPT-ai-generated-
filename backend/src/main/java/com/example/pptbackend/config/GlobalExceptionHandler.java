package com.example.pptbackend.config;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.util.Map;

/**
 * 统一 JSON 错误体 {@code {"message":"..."}}，与前端 axios 解析一致；区分配置类错误与上游大模型错误。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> dataAccess(DataAccessException exception) {
        log.error("Database error", exception);
        String detail = exception.getMostSpecificCause() != null
            ? exception.getMostSpecificCause().getMessage()
            : exception.getMessage();
        String msg = "数据库访问失败，请确认 PostgreSQL 已启动且表结构已更新。"
            + (detail != null && !detail.isBlank() ? " 详情：" + detail : "");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> generic(Exception exception) {
        if (exception instanceof AsyncRequestNotUsableException async) {
            clientDisconnected(async);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        log.error("Unhandled error", exception);
        String msg = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("message", "服务器内部错误：" + msg));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(EntityNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", exception.getMessage()));
    }

    /** Spring 6：无 Controller 匹配时（常见于后端未重启、接口未加载） */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> noResourceFound(NoResourceFoundException exception) {
        String path = exception.getResourcePath() != null ? exception.getResourcePath() : "";
        String msg = path.contains("outline/regenerate")
            ? "接口未加载：请停止并重新启动后端（在 backend 目录执行 mvn spring-boot:run），再试「重新生成大纲」。"
            : "请求路径不存在：" + path;
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", msg));
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

    /**
     * 客户端已断开（刷新/关页/超时），无需再写响应体；降级日志避免误判为服务端错误。
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void clientDisconnected(AsyncRequestNotUsableException exception) {
        if (exception.getCause() instanceof IOException io && isClientAbort(io)) {
            return;
        }
        if (exception.getCause() == null
            || exception.getMessage() == null
            || exception.getMessage().contains("interrupted")) {
            return;
        }
    }

    private static boolean isClientAbort(IOException io) {
        String msg = io.getMessage();
        return msg != null && (msg.contains("Connection reset") || msg.contains("interrupted"));
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
