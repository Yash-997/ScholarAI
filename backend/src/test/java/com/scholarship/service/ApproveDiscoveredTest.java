package com.scholarship.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scholarship.entity.ScholarshipEntity;
import com.scholarship.repository.ScholarshipRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApproveDiscoveredTest {

    @SuppressWarnings("unchecked")
    private ScholarshipIngestionService service(ScholarshipRepository repo) {
        return new ScholarshipIngestionService(repo, mock(ObjectProvider.class), mock(ObjectProvider.class), new ObjectMapper(),
                mock(ScholarshipUpsertService.class), mock(LlmAvailability.class),
                Clock.fixed(Instant.parse("2026-10-17T06:00:00Z"), ZoneId.of("Asia/Kolkata")));
    }

    private ScholarshipEntity staged() {
        ScholarshipEntity s = TestSchemes.scheme(5, "Discovered Programme 2026-27");
        s.setVerificationStatus("PENDING_REVIEW");
        s.setExternalKey("b4s:5");
        s.setState(null);
        s.setCourse(null);
        s.setCategoryCriteria(null);
        s.setGenderRequirements(null);
        return s;
    }

    @Test
    void aDiscoveredRecordWithoutCriteriaCannotBeApproved_becauseEmptyWouldMatchEveryone() {
        ScholarshipRepository repo = mock(ScholarshipRepository.class);
        ScholarshipEntity s = staged();
        when(repo.findById(5L)).thenReturn(Optional.of(s));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service(repo).approveScholarship(5L));
        assertTrue(e.getMessage().contains("before approving"));
        assertEquals("PENDING_REVIEW", s.getVerificationStatus());
        verify(repo, never()).save(any());
    }

    @Test
    void onceCriteriaAreSetItCanBeApproved() {
        ScholarshipRepository repo = mock(ScholarshipRepository.class);
        ScholarshipEntity s = staged();
        s.setState("ALL");
        s.setCourse("Engineering");
        s.setCategoryCriteria("ALL");
        s.setGenderRequirements("Female");
        when(repo.findById(5L)).thenReturn(Optional.of(s));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        assertEquals("VERIFIED", service(repo).approveScholarship(5L).verificationStatus());
    }
}
