package com.dms.provenance;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
import com.dms.audit.AuditLogRepository;
import com.dms.evaluation.EvaluationRepository;
import com.dms.logbook.LogbookService;
import com.dms.outcome.OutcomeService;
import com.dms.session.AcademicSession;
import com.dms.submission.SubmissionRepository;
import com.dms.submission.SubmissionVersionRepository;
import com.dms.user.Programme;
import com.dms.user.StudentProfile;
import com.dms.user.SupervisorProfile;
import com.dms.user.User;
import com.dms.viva.VivaScheduleRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * The logbook joins the sealed facts only once a row is signed. That keeps every
 * certificate issued before phase 13 verifying, and makes a later signature move
 * the record -- which is the behaviour a seal is supposed to have.
 */
@ExtendWith(MockitoExtension.class)
class ProvenanceLogbookFactTest {

    @Mock private AllocationRepository allocationRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private SubmissionVersionRepository versionRepository;
    @Mock private EvaluationRepository evaluationRepository;
    @Mock private VivaScheduleRepository vivaRepository;
    @Mock private CertificateRepository certificateRepository;
    @Mock private LogbookService logbookService;
    @Mock private OutcomeService outcomeService;

    @InjectMocks private ProvenanceService service;

    @Test
    void noSignedMeetingMeansNoLogbookFactAtAll() {
        Allocation allocation = allocation();
        stubEmptyRecord(allocation);
        when(logbookService.sealedFactsFor(allocation)).thenReturn(List.of());

        Map<String, String> facts = service.factsFor(allocation);

        assertFalse(facts.containsKey("logbook"),
                "an older certificate must not fail because a key it never had is now present");
    }

    @Test
    void aSignedMeetingIsListedByNumberAndDigest() {
        Allocation allocation = allocation();
        stubEmptyRecord(allocation);
        when(logbookService.sealedFactsFor(allocation)).thenReturn(List.of("1:aaaa", "2:bbbb"));

        Map<String, String> facts = service.factsFor(allocation);

        assertEquals("1:aaaa | 2:bbbb", facts.get("logbook"));
        assertTrue(facts.keySet().stream().toList().indexOf("logbook") > facts.keySet().stream().toList().indexOf("viva"),
                "appended after the existing facts so the canonical prefix is unchanged");
    }

    @Test
    void signingAMeetingAfterIssueChangesTheDigest() {
        Allocation allocation = allocation();
        stubEmptyRecord(allocation);
        when(logbookService.sealedFactsFor(allocation)).thenReturn(List.of()).thenReturn(List.of("1:aaaa"));

        String before = service.digestFor(allocation);
        String after = service.digestFor(allocation);

        assertNotEquals(before, after, "the record moved after it was sealed");
    }

    private void stubEmptyRecord(Allocation allocation) {
        when(submissionRepository.findByAllocationOrderByMilestoneSequenceNoAsc(allocation)).thenReturn(List.of());
        // factsFor sorts this list in place, as the real repository result allows.
        when(evaluationRepository.findByAllocation(allocation)).thenReturn(new ArrayList<>());
        when(vivaRepository.findByAllocation(allocation)).thenReturn(Optional.empty());
    }

    private static Allocation allocation() {
        User studentUser = new User();
        studentUser.setEmail("student@college.edu");
        studentUser.setFullName("Test Student");
        StudentProfile student = new StudentProfile();
        student.setRollNo("24MCS001");
        student.setProgramme(Programme.MTECH);
        student.setUser(studentUser);

        User guideUser = new User();
        guideUser.setEmail("guide@college.edu");
        guideUser.setFullName("Dr Test");
        SupervisorProfile guide = new SupervisorProfile();
        guide.setUser(guideUser);

        AcademicSession session = new AcademicSession();
        session.setLabel("2026-27");

        Allocation allocation = new Allocation();
        allocation.setId(11L);
        allocation.setStudent(student);
        allocation.setSupervisor(guide);
        allocation.setSession(session);
        return allocation;
    }
}
