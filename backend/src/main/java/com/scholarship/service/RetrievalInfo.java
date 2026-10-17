package com.scholarship.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Describes how retrieval works right now so nothing in the API or UI over-claims. "semantic" is reported only when a
 * real embedding model is configured; otherwise retrieval is keyword matching.
 */
@Component
public class RetrievalInfo {

    private final boolean semantic;
    private final double minSimilarity;

    public RetrievalInfo(@Value("${app.embedding.provider:lexical}") String provider,
                         @Value("${app.retrieval.min-similarity:}") String configuredMin) {
        this.semantic = "openai-compatible".equalsIgnoreCase(provider);
        double def = semantic ? 0.35 : 0.2;
        double v = def;
        try {
            if (configuredMin != null && !configuredMin.isBlank()) v = Double.parseDouble(configuredMin.trim());
        } catch (NumberFormatException ignored) {
            // keep the default
        }
        this.minSimilarity = v;
    }

    /** Test/default constructor: keyword retrieval. */
    public RetrievalInfo() {
        this("lexical", "");
    }

    public String mode() {
        return semantic ? "semantic" : "keyword";
    }

    public double minSimilarity() {
        return minSimilarity;
    }
}
