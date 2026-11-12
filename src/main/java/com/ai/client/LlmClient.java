package com.ai.client;

import com.ai.Stream.StreamingChunkHandler;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LlmClient {

    private static final int TIMEOUT_SECONDS = 10;
    private static final int MAX_RETRY = 2;

    private final ChatLanguageModel chatModel;
    private final StreamingChatLanguageModel streamingChatModel;

    public String chat(String message) {
        return safeChat(message, () -> "系统暂时繁忙，请稍后再试。");
    }

    public String safeChat(String message, Supplier<String> fallbackSupplier) {
        long start = System.currentTimeMillis();
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                String result = chatModel.generate(message);
                long latency = System.currentTimeMillis() - start;
                log.info("LLM success attempt={} latency={}ms", attempt, latency);
                return result;
            } catch (Exception e) {
                log.warn("LLM failed attempt={}", attempt, e);
            }
        }

        long latency = System.currentTimeMillis() - start;
        log.error("LLM fallback latency={}ms", latency);
        String fallback = fallbackSupplier.get();
        return fallback;
    }

    public String streamChat(
            String requestId,
            String message,
            StreamingChunkHandler handler,
            Supplier<String> fallbackSupplier) {

        long start = System.currentTimeMillis();
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            CompletableFuture<String> resultFuture = new CompletableFuture<>();
            StringBuilder fullContent = new StringBuilder();

            try {
                streamingChatModel.generate(message, new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        if (token == null || token.isEmpty()) {
                            return;
                        }
                        fullContent.append(token);
                        handler.onChunk(token);
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        String content = response == null || response.content() == null
                                ? fullContent.toString()
                                : response.content().text();
                        resultFuture.complete(content == null ? fullContent.toString() : content);
                    }

                    @Override
                    public void onError(Throwable error) {
                        resultFuture.completeExceptionally(error);
                    }
                });

                String result = resultFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                long latency = System.currentTimeMillis() - start;
                log.info("LLM stream success requestId={} attempt={} latency={}ms", requestId, attempt, latency);
                handler.onComplete(result);
                return result;
            } catch (TimeoutException e) {
                log.warn("LLM stream timeout requestId={} attempt={}", requestId, attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("LLM stream interrupted requestId={} attempt={}", requestId, attempt, e);
            } catch (ExecutionException e) {
                log.warn("LLM stream failed requestId={} attempt={}", requestId, attempt, e.getCause());
            } catch (Exception e) {
                log.warn("LLM stream failed requestId={} attempt={}", requestId, attempt, e);
            }
        }

        long latency = System.currentTimeMillis() - start;
        log.error("LLM stream fallback requestId={} latency={}ms", requestId, latency);
        String fallback = fallbackSupplier.get();
        handler.onChunk(fallback);
        handler.onComplete(fallback);
        return fallback;
    }
}
