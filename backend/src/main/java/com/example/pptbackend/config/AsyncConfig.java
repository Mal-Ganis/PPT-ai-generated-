package com.example.pptbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
        return executor;
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
