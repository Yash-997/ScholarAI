package com.scholarship.service;

import com.scholarship.dto.ScholarshipDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * Compatibility delegate service forwarding admin ingestion operations to ScholarshipIngestionService.
 */
@Service
public class AdminIngestionService {

    private final ScholarshipIngestionService scholarshipIngestionService;

    @Autowired
    public AdminIngestionService(ScholarshipIngestionService scholarshipIngestionService) {
        this.scholarshipIngestionService = scholarshipIngestionService;
    }

    public ScholarshipDto ingestSource(String sourceUrl, MultipartFile pdfFile) {
        return scholarshipIngestionService.ingestSource(sourceUrl, pdfFile);
    }

    public ScholarshipDto approveScholarship(Long id) {
        return scholarshipIngestionService.approveScholarship(id);
    }

    public List<ScholarshipDto> getPendingScholarships() {
        return scholarshipIngestionService.getPendingScholarships();
    }

    public String extractTextFromPdf(InputStream inputStream) throws Exception {
        return scholarshipIngestionService.extractTextFromPdf(inputStream);
    }

    public String extractTextFromUrl(String url) {
        return scholarshipIngestionService.extractTextFromUrl(url);
    }
}
