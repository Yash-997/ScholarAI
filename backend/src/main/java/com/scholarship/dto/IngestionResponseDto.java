package com.scholarship.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record IngestionResponseDto(
        @JsonProperty("total_processed")
        int totalProcessed,

        @JsonProperty("total_chunks_indexed")
        int totalChunksIndexed,

        String status,
        String message,

        @JsonProperty("indexed_scholarship_ids")
        List<Long> indexedScholarshipIds
) {}
