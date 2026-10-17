package com.scholarship.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Whether a usable LLM credential is configured. The application starts without one (a placeholder keeps Spring AI's
 * auto-configuration happy); every LLM call site checks this first and falls back to deterministic output instead of
 * sending a request that is certain to fail.
 */
@Component
public class LlmAvailability {

    static final String PLACEHOLDER = "not-configured";

    private final boolean configured;

    public LlmAvailability(@Value("${GROQ_API_KEY:}") String apiKey) {
        this.configured = apiKey != null && !apiKey.isBlank() && !PLACEHOLDER.equals(apiKey.trim());
    }

    public boolean isConfigured() {
        return configured;
    }
}
