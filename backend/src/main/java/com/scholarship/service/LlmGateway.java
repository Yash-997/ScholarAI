package com.scholarship.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The only place the chat model is called from request paths. Never throws: callers get either text or a reason
 * the model was not usable, and decide on a deterministic fallback.
 */
@Component
public class LlmGateway {

    public enum Failure { NOT_CONFIGURED, TIMEOUT, ERROR, EMPTY }

    /** Exactly one of {@code text} / {@code failure} is set. */
    public record Reply(String text, Failure failure) {
        public boolean ok() {
            return failure == null;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(LlmGateway.class);

    private final ChatClient chatClient;
    private final LlmAvailability availability;
    private final long timeoutSeconds;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public LlmGateway(ObjectProvider<ChatModel> chatModelProvider, LlmAvailability availability,
                      @Value("${app.llm.timeout-seconds:25}") long timeoutSeconds) {
        ChatModel model = null;
        try {
            model = chatModelProvider.getIfAvailable();
        } catch (Exception e) {
            log.warn("Chat model unavailable: {}", e.getMessage());
        }
        this.chatClient = model != null ? ChatClient.create(model) : null;
        this.availability = availability;
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isUsable() {
        return chatClient != null && availability.isConfigured();
    }

    public Reply complete(String system, String user) {
        if (!isUsable()) {
            return new Reply(null, Failure.NOT_CONFIGURED);
        }
        Future<String> call = executor.submit(() -> chatClient.prompt().system(system).user(user).call().content());
        try {
            String text = call.get(timeoutSeconds, TimeUnit.SECONDS);
            if (text == null || text.isBlank()) {
                return new Reply(null, Failure.EMPTY);
            }
            return new Reply(text.trim(), null);
        } catch (TimeoutException e) {
            call.cancel(true);
            log.warn("LLM call timed out after {}s", timeoutSeconds);
            return new Reply(null, Failure.TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            call.cancel(true);
            return new Reply(null, Failure.ERROR);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String msg = String.valueOf(cause.getMessage());
            log.warn("LLM call failed: {}: {}", cause.getClass().getSimpleName(), msg.length() > 200 ? msg.substring(0, 200) : msg);
            return new Reply(null, Failure.ERROR);
        }
    }
}
