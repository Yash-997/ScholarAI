package com.scholarship.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ErrorResponseDto(
        int status,
        String error,
        String message,
        String path,
        List<String> details,
        OffsetDateTime timestamp
) {
    public ErrorResponseDto(int status, String error, String message, String path, List<String> details) {
        this(status, error, message, path, details, OffsetDateTime.now());
    }
}
