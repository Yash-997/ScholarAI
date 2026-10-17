package com.scholarship.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScholarshipChatResponseDto {

    /** One scheme the answer drew on. All fields come from the database. */
    public record Source(Long id, String name, String url, String status, LocalDate lastVerified,
                         String academicYear, LocalDate deadline, String deadlineOrigin, String sourceUrl, OffsetDateTime fetchedAt) {
    }

    private String reply;
    private List<String> sourceCitations = new ArrayList<>();
    private List<Source> sources = new ArrayList<>();
    /** LLM when the model wrote the answer, FALLBACK when it was composed from records without the model. */
    private String mode = "FALLBACK";
    /** Why the model was not used (NOT_CONFIGURED, TIMEOUT, ERROR, EMPTY, REJECTED), null in LLM mode. */
    private String fallbackReason;
    /** Always "keyword": retrieval matches shared words, it is not semantic search. */
    private String retrievalMode = "keyword";
    /** Student details the assistant understood from this conversation; absent keys were not specified. */
    private Map<String, Object> understoodProfile = new LinkedHashMap<>();
    private OffsetDateTime timestamp;

    public ScholarshipChatResponseDto() {
        this.timestamp = OffsetDateTime.now();
    }

    public ScholarshipChatResponseDto(String reply, List<String> sourceCitations) {
        this.reply = reply;
        this.sourceCitations = sourceCitations;
        this.timestamp = OffsetDateTime.now();
    }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }

    public List<String> getSourceCitations() { return sourceCitations; }
    public void setSourceCitations(List<String> sourceCitations) { this.sourceCitations = sourceCitations; }

    public List<Source> getSources() { return sources; }
    public void setSources(List<Source> sources) { this.sources = sources; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getFallbackReason() { return fallbackReason; }
    public void setFallbackReason(String fallbackReason) { this.fallbackReason = fallbackReason; }

    public String getRetrievalMode() { return retrievalMode; }
    public void setRetrievalMode(String retrievalMode) { this.retrievalMode = retrievalMode; }

    public Map<String, Object> getUnderstoodProfile() { return understoodProfile; }
    public void setUnderstoodProfile(Map<String, Object> understoodProfile) { this.understoodProfile = understoodProfile; }

    public OffsetDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(OffsetDateTime timestamp) { this.timestamp = timestamp; }
}
