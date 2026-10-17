package com.scholarship.dto;

public class ChatMessageDto {
    private String sender; // "user" or "assistant"
    private String text;

    public ChatMessageDto() {}

    public ChatMessageDto(String sender, String text) {
        this.sender = sender;
        this.text = text;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
