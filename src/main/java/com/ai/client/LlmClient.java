package com.ai.client;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class LlmClient {

    private final ChatLanguageModel chatModel;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private static final int TIMEOUT_SECONDS = 10;
    private static final int MAX_RETRY = 2;

    public String chat(String message) {
        return safeChat(
                message,
                () -> "系统暂时繁忙，请稍后再试。");
    }

    public String safeChat(String message, Supplier<String> fallbackSupplier) {
        long start = System.currentTimeMillis();
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            Future<String> future = executor.submit(
                    () -> chatModel.generate(message));
            try {
                String result = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

                long latency = System.currentTimeMillis() - start;

                log.info(
                        "LLM success attempt={} latency={}ms",
                        attempt,
                        latency);

                return result;
            } catch (TimeoutException e) {

                future.cancel(true);

                log.warn(
                        "LLM timeout attempt={}",
                        attempt);

            } catch (Exception e) {

                log.warn(
                        "LLM failed attempt={}",
                        attempt,
                        e);
            }
        }
        long latency = System.currentTimeMillis() - start;

        log.error(
                "LLM fallback latency={}ms",
                latency);

        return fallbackSupplier.get();
    }

}
