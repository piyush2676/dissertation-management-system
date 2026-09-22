package com.dms.readiness;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
import com.dms.allocation.AllocationService;
import com.dms.allocation.AllocationStatus;
import com.dms.evaluation.Evaluation;
import com.dms.evaluation.EvaluationRepository;
import com.dms.evaluation.EvaluationService;
import com.dms.evaluation.RubricCriterion;
import com.dms.logbook.LogbookBoard;
import com.dms.logbook.LogbookEntryStatus;
import com.dms.logbook.LogbookService;
import com.dms.outcome.OutcomeBoard;
import com.dms.outcome.OutcomeIndexing;
import com.dms.outcome.OutcomeKind;
import com.dms.outcome.OutcomeService;
import com.dms.outcome.OutcomeStatus;
import com.dms.panel.PanelService;
import com.dms.readiness.ReadinessLedger.State;
import com.dms.recommendation.RecommendationService;
import com.dms.session.AcademicSession;
import com.dms.session.DeliverableType;
import com.dms.session.DissertationPhase;
import com.dms.session.Milestone;
import com.dms.session.MilestoneRepository;
import com.dms.submission.PlagiarismCheck;
import com.dms.submission.Submission;
import com.dms.submission.SubmissionRepository;
import com.dms.submission.SubmissionService;
import com.dms.submission.SubmissionStatus;
import com.dms.submission.SubmissionVersion;
import com.dms.submission.SubmissionVersionRepository;
import com.dms.user.Programme;
import com.dms.user.StudentProfile;
import com.dms.user.SupervisorProfile;
import com.dms.user.User;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * One test per rule, each stating the rule. The ledger is the argument against a
 * locked button, so what it says had better be exactly what the guidelines say.
 */
@ExtendWith(MockitoExtension.class)
class ReadinessServiceTest {

    @Mock private AllocationRepository allocationRepository;
    @Mock private AllocationService allocationService;
    @Mock private EvaluationService evaluationService;
    @Mock private EvaluationRepository evaluationRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private SubmissionVersionRepository versionRepository;
    @Mock private SubmissionService submissionService;
    @Mock private MilestoneRepository milestoneRepository;
    @Mock private OutcomeService outcomeService;
    @Mock private LogbookService logbookService;
    @Mock private PanelService panelService;
    @Mock private RecommendationService recommendationService;

    @InjectMocks private ReadinessService service;

    // ---- internal marks, the gate --------------------------------------------

    @Test
    void halfThePhaseMaximumMeetsTheGate() {
        Allocation allocation = allocation();
        when(evaluationService.rubricFor(allocation)).thenReturn(rubricOf(200));
        when(evaluationRepository.findByAllocation(allocation)).thenReturn(List.of(evaluation("100.00")));

        assertTrue(service.internalMarksMet(allocation), "100 of 200 is exactly 50%");
    }

    @Test
    void justUnderHalfDoesNot() {
        Allocation allocation = allocation();
        when(evaluationService.rubricFor(allocation)).thenReturn(rubricOf(200));
        when(evaluationRepository.findByAllocation(allocation)).thenReturn(List.of(evaluation("99.00")));

        assertFalse(service.internalMarksMet(allocation));
    }

    @Test
    void theGateReadsTheAverageAcrossExaminers() {
        Allocation allocation = allocation();
        when(evaluationService.rubricFor(allocation)).thenReturn(rubricOf(100));
        when(evaluationRepository.findByAllocation(allocation))
                .thenReturn(List.of(evaluation("40.00"), evaluation("70.00")));

        assertTrue(service.internalMarksMet(allocation), "(40 + 70) / 2 = 55");
    }

    @Test
    void anUnmarkedStudentIsNotEligibleAndTheRuleSaysSo() {
        Allocation allocation = allocation();
        when(evaluationService.rubricFor(allocation)).thenReturn(rubricOf(100));
        when(evaluationRepository.findByAllocation(allocation)).thenReturn(List.of());

        ReadinessLedger.Rule rule = service.internalMarks(allocation);

        assertEquals(State.NOT_MET, rule.state());
        assertEquals("Not yet marked.", rule.summary());
    }

    @Test
    void aStudentOutsideBothPhasesHasNoRubricAndCannotPass() {
        Allocation allocation = allocation();
        when(evaluationService.rubricFor(allocation)).thenReturn(List.of());
        when(evaluationRepository.findByAllocation(allocation)).thenReturn(List.of(evaluation("100.00")));

        assertFalse(service.internalMarksMet(allocation), "no maximum means nothing to be half of");
    }

    // ---- deliverables --------------------------------------------------------

    @Test
    void anApprovedSlotMeetsItsDocumentWithTheVersionDigestAsEvidence() {
        Allocation allocation = allocation();
        Milestone slot = milestone(1L, DeliverableType.SYNOPSIS);
        Submission approved = submission(allocation, slot, SubmissionStatus.APPROVED);
        SubmissionVersion v2 = version(approved, 2, "ab".repeat(32));
        when(milestoneRepository.findBySessionOrderBySequenceNoAsc(allocation.getSession())).thenReturn(List.of(slot));
        when(submissionRepository.findByAllocationOrderByMilestoneSequenceNoAsc(allocation)).thenReturn(List.of(approved));
        when(versionRepository.findFirstBySubmissionOrderByVersionNoDesc(approved)).thenReturn(Optional.of(v2));
        when(outcomeService.countedRowsFor(allocation)).thenReturn(List.of());

        ReadinessLedger.Rule synopsis = service.deliverables(allocation).stream()
                .filter(r -> r.code().equals("DOC_SYNOPSIS")).findFirst().orElseThrow();

        assertEquals(State.MET, synopsis.state());
        assertEquals("ab".repeat(32), synopsis.evidence().get(0).ref());
    }

    @Test
    void aFiledButUnapprovedSlotIsNotMet() {
        Allocation allocation = allocation();
        Milestone slot = milestone(1L, DeliverableType.SYNOPSIS);
        Submission underReview = submission(allocation, slot, SubmissionStatus.UNDER_REVIEW);
        when(milestoneRepository.findBySessionOrderBySequenceNoAsc(allocation.getSession())).thenReturn(List.of(slot));
        when(submissionRepository.findByAllocationOrderByMilestoneSequenceNoAsc(allocation)).thenReturn(List.of(underReview));
        when(outcomeService.countedRowsFor(allocation)).thenReturn(List.of());

        ReadinessLedger.Rule synopsis = service.deliverables(allocation).stream()
                .filter(r -> r.code().equals("DOC_SYNOPSIS")).findFirst().orElseThrow();

        assertEquals(State.NOT_MET, synopsis.state());
    }

    @Test
    void aDocumentNoSlotCollectsIsNotApplicableRatherThanFailed() {
        Allocation allocation = allocation();
        when(milestoneRepository.findBySessionOrderBySequenceNoAsc(allocation.getSession())).thenReturn(List.of());
        when(submissionRepository.findByAllocationOrderByMilestoneSequenceNoAsc(allocation)).thenReturn(List.of());
        when(outcomeService.countedRowsFor(allocation)).thenReturn(List.of());

        List<ReadinessLedger.Rule> rules = service.deliverables(allocation);

        assertTrue(rules.stream().filter(r -> r.code().startsWith("DOC_"))
                .allMatch(r -> r.state() == State.NOT_APPLICABLE));
    }

    @Test
    void researchPapersComeFromVerifiedOutcomesNotUploads() {
        Allocation allocation = allocation();
        when(milestoneRepository.findBySessionOrderBySequenceNoAsc(allocation.getSession())).thenReturn(List.of());
        when(submissionRepository.findByAllocationOrderByMilestoneSequenceNoAsc(allocation)).thenReturn(List.of());
        when(outcomeService.countedRowsFor(allocation)).thenReturn(List.of(
                paper(1L, OutcomeKind.CONFERENCE_PAPER, OutcomeIndexing.IEEE)));

        List<ReadinessLedger.Rule> rules = service.deliverables(allocation);

        assertEquals(State.MET, rules.stream().filter(r -> r.code().equals("PAPER_1")).findFirst().orElseThrow().state());
        assertEquals(State.NOT_MET, rules.stream().filter(r -> r.code().equals("PAPER_2")).findFirst().orElseThrow().state());
    }

    // ---- publication rule ----------------------------------------------------

    @Test
    void oneScopusJournalPaperMeetsThePublicationRule() {
        Allocation allocation = allocation();
        when(outcomeService.countedRowsFor(allocation)).thenReturn(List.of(
                paper(1L, OutcomeKind.JOURNAL_PAPER, OutcomeIndexing.SCOPUS)));

        assertEquals(State.MET, service.publication(allocation).state());
    }

    @Test
    void oneConferencePaperDoesNotTwoDo() {
        Allocation allocation = allocation();
        when(outcomeService.countedRowsFor(allocation))
                .thenReturn(List.of(paper(1L, OutcomeKind.CONFERENCE_PAPER, OutcomeIndexing.IEEE)))
                .thenReturn(List.of(paper(1L, OutcomeKind.CONFERENCE_PAPER, OutcomeIndexing.IEEE),
                        paper(2L, OutcomeKind.CONFERENCE_PAPER, OutcomeIndexing.SCOPUS)));

        assertEquals(State.NOT_MET, service.publication(allocation).state());
        assertEquals(State.MET, service.publication(allocation).state());
    }

    @Test
    void anUnindexedJournalPaperDoesNotCount() {
        Allocation allocation = allocation();
        when(outcomeService.countedRowsFor(allocation)).thenReturn(List.of(
                paper(1L, OutcomeKind.JOURNAL_PAPER, OutcomeIndexing.OTHER)));

        assertEquals(State.NOT_MET, service.publication(allocation).state());
    }

    // ---- plagiarism ----------------------------------------------------------

    @Test
    void theSimilarityRuleReadsTheCheckOnTheLatestThesisVersion() {
        Allocation allocation = allocation();
        Milestone slot = milestone(3L, DeliverableType.FINAL_THESIS);
        Submission thesis = submission(allocation, slot, SubmissionStatus.APPROVED);
        SubmissionVersion v3 = version(thesis, 3, "cd".repeat(32));
        PlagiarismCheck check = new PlagiarismCheck();
        check.setSimilarityPercent(new BigDecimal("6.50"));
        check.setAiPercent(BigDecimal.ZERO);
        check.setCheckedBy(user(7L, "guide@college.edu", "Dr Test"));
        check.setCheckedAt(Instant.now());
        when(submissionRepository.findByAllocationOrderByMilestoneSequenceNoAsc(allocation)).thenReturn(List.of(thesis));
        when(submissionService.latestCheckFor(thesis)).thenReturn(Optional.of(check));
        when(versionRepository.findFirstBySubmissionOrderByVersionNoDesc(thesis)).thenReturn(Optional.of(v3));

        ReadinessLedger.Rule rule = service.plagiarism(allocation);

        assertEquals(State.MET, rule.state());
        assertEquals("cd".repeat(32), rule.evidence().get(0).ref(), "pinned to the version that was checked");
    }

    @Test
    void noThesisMeansNoSimilarityRuleMet() {
        Allocation allocation = allocation();
        when(submissionRepository.findByAllocationOrderByMilestoneSequenceNoAsc(allocation)).thenReturn(List.of());

        assertEquals(State.NOT_MET, service.plagiarism(allocation).state());
    }

    // ---- logbook -------------------------------------------------------------

    @Test
    void oneCountersignedMeetingMeetsTheReportCardRule() {
        Allocation allocation = allocation();
        when(logbookService.signedRowsFor(allocation)).thenReturn(List.of(
                new LogbookBoard.Row(1L, 1, Instant.now(), "a", "b", null, LogbookEntryStatus.SIGNED,
                        null, "Dr Test", Instant.now(), "ef".repeat(32))));

        ReadinessLedger.Rule rule = service.logbook(allocation);

        assertEquals(State.MET, rule.state());
        assertEquals("ef".repeat(32), rule.evidence().get(0).ref());
    }

    // ---- the ledger as a whole -----------------------------------------------

    @Test
    void vivaEligibilityIsTheMarksRuleAloneEverythingElseIsAdvisory() {
        Allocation allocation = allocation();
        when(evaluationService.rubricFor(allocation)).thenReturn(rubricOf(100));
        when(evaluationRepository.findByAllocation(allocation)).thenReturn(List.of(evaluation("60.00")));
        lenient().when(milestoneRepository.findBySessionOrderBySequenceNoAsc(any())).thenReturn(List.of());
        lenient().when(submissionRepository.findByAllocationOrderByMilestoneSequenceNoAsc(any())).thenReturn(List.of());
        lenient().when(outcomeService.countedRowsFor(any())).thenReturn(List.of());
        lenient().when(logbookService.signedRowsFor(any())).thenReturn(List.of());
        lenient().when(panelService.membersOf(any())).thenReturn(List.of());
        lenient().when(recommendationService.viewFor(any())).thenReturn(java.util.Optional.empty());

        ReadinessLedger ledger = service.ledgerFor(allocation, ReadinessService.Audience.OFFICE);

        assertTrue(ledger.vivaEligible());
        assertFalse(ledger.complete(), "no papers, no logbook, no thesis: the checklist is open");
    }

    // ---- Annexure-6 is confidential ------------------------------------------

    @Test
    void theOfficeSeesTheVerdictAndTheStudentDoesNot() {
        Allocation allocation = allocation();
        when(recommendationService.viewFor(allocation)).thenReturn(Optional.of(
                new com.dms.recommendation.RecommendationView(11L, "24MCS001", "Test Student", null, null,
                        "Dr Test", com.dms.recommendation.Verdict.MINOR_REVISIONS,
                        null, null, null, "Add the ablation table.", "Why DWT over DCT?",
                        "Dr Test", Instant.now(), Instant.now())));

        ReadinessLedger.Rule office = service.recommendation(allocation, ReadinessService.Audience.OFFICE);
        ReadinessLedger.Rule student = service.recommendation(allocation, ReadinessService.Audience.STUDENT);

        assertTrue(office.summary().startsWith("[B]"));
        assertFalse(student.summary().contains("[B]"), "the sheet is confidential to the supervisor and the office");
        assertTrue(student.evidence().isEmpty(), "not even who filed it, on the student's own page");
        assertEquals(office.state(), student.state(), "whether the thesis is cleared is not the secret");
    }

    @Test
    void aVerdictThatSendsTheThesisBackIsNotMetForEitherReader() {
        Allocation allocation = allocation();
        when(recommendationService.viewFor(allocation)).thenReturn(Optional.of(
                new com.dms.recommendation.RecommendationView(11L, "24MCS001", "Test Student", null, null,
                        "Dr Test", com.dms.recommendation.Verdict.MAJOR_REVISIONS,
                        null, null, null, "Rewrite chapter 4.", null,
                        "Dr Test", Instant.now(), Instant.now())));

        assertEquals(State.NOT_MET, service.recommendation(allocation, ReadinessService.Audience.OFFICE).state());
        assertEquals(State.NOT_MET, service.recommendation(allocation, ReadinessService.Audience.STUDENT).state());
    }

    // ---- fixtures -----------------------------------------------------------

    private static List<RubricCriterion> rubricOf(int total) {
        RubricCriterion c = new RubricCriterion();
        c.setMaxMarks(total);
        c.setWeightage(total);
        return List.of(c);
    }

    private static Evaluation evaluation(String total) {
        Evaluation e = new Evaluation();
        e.setExaminer(user(7L, "guide@college.edu", "Dr Test"));
        e.setTotal(new BigDecimal(total));
        e.setSubmittedAt(Instant.now());
        return e;
    }

    private static OutcomeBoard.Row paper(Long id, OutcomeKind kind, OutcomeIndexing indexing) {
        return new OutcomeBoard.Row(id, kind, "A paper", "Venue", indexing, OutcomeStatus.PUBLISHED,
                "10.1000/xyz", LocalDate.now(), null, true, "PG Coordinator", Instant.now(), null);
    }

    private static Milestone milestone(Long id, DeliverableType type) {
        Milestone m = new Milestone();
        m.setId(id);
        m.setName(type.getLabel() + " slot");
        m.setDeliverable(type);
        m.setPhase(DissertationPhase.FINAL);
        m.setSequenceNo(1);
        m.setDueDate(LocalDate.now());
        return m;
    }

    private static Submission submission(Allocation allocation, Milestone milestone, SubmissionStatus status) {
        Submission s = new Submission();
        s.setId(milestone.getId() * 10);
        s.setAllocation(allocation);
        s.setMilestone(milestone);
        s.setStatus(status);
        s.setDecidedBy(user(7L, "guide@college.edu", "Dr Test"));
        s.setDecidedAt(Instant.now());
        return s;
    }

    private static SubmissionVersion version(Submission submission, int no, String sha) {
        SubmissionVersion v = new SubmissionVersion();
        v.setId(submission.getId() * 10 + no);
        v.setSubmission(submission);
        v.setVersionNo(no);
        v.setSha256(sha);
        return v;
    }

    private static User user(Long id, String email, String name) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(name);
        return user;
    }

    private static Allocation allocation() {
        StudentProfile student = new StudentProfile();
        student.setId(1L);
        student.setRollNo("24MCS001");
        student.setProgramme(Programme.MTECH);
        student.setSemester(4);
        student.setUser(user(1L, "student@college.edu", "Test Student"));
        SupervisorProfile guide = new SupervisorProfile();
        guide.setId(7L);
        guide.setUser(user(7L, "guide@college.edu", "Dr Test"));
        AcademicSession session = new AcademicSession();
        session.setId(30L);
        session.setLabel("2026-27");
        Allocation allocation = new Allocation();
        allocation.setId(11L);
        allocation.setStudent(student);
        allocation.setSupervisor(guide);
        allocation.setSession(session);
        allocation.setStatus(AllocationStatus.ACCEPTED);
        return allocation;
    }
}
