package com.example.pptbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@EnableAsync
public class AsyncConfig {

    private static final AtomicInteger LLM_HTTP_THREAD_SEQ = new AtomicInteger();

    @Bean(name = "pptTaskExecutor")
    public Executor pptTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ppt-deferred-index-");
        executor.initialize();
        // 正文生成 / 大纲异步索引在后台线程执行，须继承发起请求时的登录上下文，否则多租户校验与 RAG 检索会失败。
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }

    /**
     * 专用于 DeepSeek 等阻塞式 HTTP；避免在 Tomcat 工作线程上直接 {@code HttpClient.send}，
     * 否则客户端/代理断开时容器可能对请求线程 {@code interrupt}，被误判为「大模型请求被中断」。
     */
    @Bean(name = "llmHttpExecutor", destroyMethod = "shutdown")
    public ExecutorService llmHttpExecutor() {
        return Executors.newFixedThreadPool(16, r -> {
            Thread t = new Thread(r, "deepseek-http-" + LLM_HTTP_THREAD_SEQ.incrementAndGet());
            t.setDaemon(false);
            return t;
        });
    }
}
