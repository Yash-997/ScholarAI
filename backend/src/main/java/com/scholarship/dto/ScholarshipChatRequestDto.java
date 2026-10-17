package com.scholarship.dto;

import java.util.ArrayList;
import java.util.List;

public class ScholarshipChatRequestDto {
    private String message;
    private List<ChatMessageDto> history = new ArrayList<>();
    /** Optional details the student already entered elsewhere in the app (for example the Eligibility form). */
    private RecommendRequestDto profile;

    public ScholarshipChatRequestDto() {}

    public ScholarshipChatRequestDto(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<ChatMessageDto> getHistory() {
        return history;
    }

    public void setHistory(List<ChatMessageDto> history) {
        this.history = history == null ? new ArrayList<>() : history;
    }

    public RecommendRequestDto getProfile() {
        return profile;
    }

    public void setProfile(RecommendRequestDto profile) {
        this.profile = profile;
    }
}
