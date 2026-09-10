package com.dms.submission;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationService;
import com.dms.allocation.AllocationStatus;
import com.dms.common.InvalidStateTransitionException;
import com.dms.common.NotFoundException;
import com.dms.session.AcademicSession;
import com.dms.session.Milestone;
import com.dms.session.MilestoneRepository;
import com.dms.storage.StorageService;
import com.dms.storage.StoredFile;
import com.dms.user.Programme;
import com.dms.user.StudentProfile;
import com.dms.user.SupervisorProfile;
import com.dms.user.User;
import com.dms.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    private static final String STUDENT_EMAIL = "student@college.edu";
    private static final String GUIDE_EMAIL = "guide@college.edu";
    private static final String OTHER_GUIDE_EMAIL = "other@college.edu";

    @Mock private SubmissionRepository submissionRepository;
    @Mock private SubmissionVersionRepository versionRepository;
    @Mock private MilestoneRepository milestoneRepository;
    @Mock private AllocationService allocationService;
    @Mock private StorageService storageService;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher events;

    @InjectMocks private SubmissionService service;

    // ---- upload -------------------------------------------------------------

    @Test
    void firstUploadCreatesTheSlotAndFilesVersionOne() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        Milestone milestone = milestone(3L, LocalDate.now().plusDays(30));

        when(allocationService.currentAllocationFor(STUDENT_EMAIL)).thenReturn(Optional.of(allocation));
        when(milestoneRepository.findById(3L)).thenReturn(Optional.of(milestone));
        when(submissionRepository.findByAllocationAndMilestone(allocation, milestone)).thenReturn(Optional.empty());
        when(submissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(storageService.store(any(), anyString())).thenReturn(stored("abc123"));
        when(versionRepository.existsBySubmissionAndSha256(any(), anyString())).thenReturn(false);

        Submission submission = service.upload(STUDENT_EMAIL, 3L, file(), "first cut");

        assertEquals(SubmissionStatus.SUBMITTED, submission.getStatus());
        assertEquals(1, submission.getCurrentVersionNo());
        assertFalse(submission.isLate(), "filed before the due date");

        ArgumentCaptor<SubmissionVersion> captor = ArgumentCaptor.forClass(SubmissionVersion.class);
        verify(versionRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getVersionNo());
        assertEquals("abc123", captor.getValue().getSha256());
    }

    @Test
    void aFirstUploadAfterTheDueDateIsMarkedLate() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        Milestone milestone = milestone(3L, LocalDate.now().minusDays(2));

        when(allocationService.currentAllocationFor(STUDENT_EMAIL)).thenReturn(Optional.of(allocation));
        when(milestoneRepository.findById(3L)).thenReturn(Optional.of(milestone));
        when(submissionRepository.findByAllocationAndMilestone(allocation, milestone)).thenReturn(Optional.empty());
        when(submissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(storageService.store(any(), anyString())).thenReturn(stored("abc123"));
        when(versionRepository.existsBySubmissionAndSha256(any(), anyString())).thenReturn(false);

        assertTrue(service.upload(STUDENT_EMAIL, 3L, file(), null).isLate());
    }

    @Test
    void aRevisionBecomesVersionTwoAndClearsTheEarlierDecision() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        Milestone milestone = milestone(3L, LocalDate.now().minusDays(30));
        Submission existing = submission(allocation, milestone, SubmissionStatus.REVISION_REQUESTED, 1);
        existing.setDecisionNote("Rework chapter 4");
        existing.setLate(false);

        when(allocationService.currentAllocationFor(STUDENT_EMAIL)).thenReturn(Optional.of(allocation));
        when(milestoneRepository.findById(3L)).thenReturn(Optional.of(milestone));
        when(submissionRepository.findByAllocationAndMilestone(allocation, milestone)).thenReturn(Optional.of(existing));
        when(submissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(storageService.store(any(), anyString())).thenReturn(stored("def456"));
        when(versionRepository.existsBySubmissionAndSha256(any(), anyString())).thenReturn(false);

        Submission submission = service.upload(STUDENT_EMAIL, 3L, file(), "reworked");

        assertEquals(2, submission.getCurrentVersionNo());
        assertEquals(SubmissionStatus.SUBMITTED, submission.getStatus());
        assertEquals(null, submission.getDecisionNote(), "the old decision must not linger");
        assertFalse(submission.isLate(), "lateness is judged on the first attempt only");
    }

    @Test
    void aStudentCannotReplaceWorkAlreadyWithTheGuide() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        Milestone milestone = milestone(3L, LocalDate.now().plusDays(5));
        Submission existing = submission(allocation, milestone, SubmissionStatus.UNDER_REVIEW, 1);

        when(allocationService.currentAllocationFor(STUDENT_EMAIL)).thenReturn(Optional.of(allocation));
        when(milestoneRepository.findById(3L)).thenReturn(Optional.of(milestone));
        when(submissionRepository.findByAllocationAndMilestone(allocation, milestone)).thenReturn(Optional.of(existing));

        assertThrows(InvalidStateTransitionException.class,
                () -> service.upload(STUDENT_EMAIL, 3L, file(), null));
        verify(storageService, never()).store(any(), anyString());
    }

    @Test
    void anIdenticalReuploadIsRefusedAndTheFileIsDropped() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        Milestone milestone = milestone(3L, LocalDate.now().plusDays(5));
        Submission existing = submission(allocation, milestone, SubmissionStatus.REVISION_REQUESTED, 1);

        when(allocationService.currentAllocationFor(STUDENT_EMAIL)).thenReturn(Optional.of(allocation));
        when(milestoneRepository.findById(3L)).thenReturn(Optional.of(milestone));
        when(submissionRepository.findByAllocationAndMilestone(allocation, milestone)).thenReturn(Optional.of(existing));
        when(storageService.store(any(), anyString())).thenReturn(stored("same"));
        when(versionRepository.existsBySubmissionAndSha256(existing, "same")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.upload(STUDENT_EMAIL, 3L, file(), null));

        verify(storageService).delete("uploads/1/3/x.pdf");
        verify(versionRepository, never()).save(any());
    }

    @Test
    void aStudentWithoutAnAcceptedGuideCannotSubmit() {
        when(allocationService.currentAllocationFor(STUDENT_EMAIL))
                .thenReturn(Optional.of(allocation(AllocationStatus.REQUESTED)));

        assertThrows(IllegalStateException.class, () -> service.upload(STUDENT_EMAIL, 3L, file(), null));
    }

    @Test
    void aMilestoneFromAnotherSessionIsNotFound() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        Milestone foreign = milestone(9L, LocalDate.now().plusDays(5));
        AcademicSession otherSession = new AcademicSession();
        otherSession.setId(99L);
        foreign.setSession(otherSession);

        when(allocationService.currentAllocationFor(STUDENT_EMAIL)).thenReturn(Optional.of(allocation));
        when(milestoneRepository.findById(9L)).thenReturn(Optional.of(foreign));

        assertThrows(NotFoundException.class, () -> service.upload(STUDENT_EMAIL, 9L, file(), null));
    }

    // ---- review -------------------------------------------------------------

    @Test
    void startingTheReviewMovesSubmittedToUnderReview() {
        Submission submission = submission(allocation(AllocationStatus.ACCEPTED),
                milestone(3L, LocalDate.now()), SubmissionStatus.SUBMITTED, 1);

        when(submissionRepository.findWithGraphById(5L)).thenReturn(Optional.of(submission));
        when(submissionRepository.existsByIdAndAllocationSupervisorUserEmail(5L, GUIDE_EMAIL)).thenReturn(true);
        when(submissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(SubmissionStatus.UNDER_REVIEW, service.startReview(GUIDE_EMAIL, 5L).getStatus());
    }

    @Test
    void anotherGuidesSubmissionIsNotFound() {
        Submission submission = submission(allocation(AllocationStatus.ACCEPTED),
                milestone(3L, LocalDate.now()), SubmissionStatus.SUBMITTED, 1);

        when(submissionRepository.findWithGraphById(5L)).thenReturn(Optional.of(submission));
        when(submissionRepository.existsByIdAndAllocationSupervisorUserEmail(5L, OTHER_GUIDE_EMAIL)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> service.startReview(OTHER_GUIDE_EMAIL, 5L));
    }

    @Test
    void aDecisionCannotBeRecordedBeforeTheReviewStarts() {
        Submission submission = submission(allocation(AllocationStatus.ACCEPTED),
                milestone(3L, LocalDate.now()), SubmissionStatus.SUBMITTED, 1);

        when(submissionRepository.findWithGraphById(5L)).thenReturn(Optional.of(submission));
        when(submissionRepository.existsByIdAndAllocationSupervisorUserEmail(5L, GUIDE_EMAIL)).thenReturn(true);

        assertThrows(InvalidStateTransitionException.class,
                () -> service.decide(GUIDE_EMAIL, 5L, SubmissionStatus.APPROVED, null));
    }

    @Test
    void requestingARevisionWithoutANoteIsRefused() {
        Submission submission = submission(allocation(AllocationStatus.ACCEPTED),
                milestone(3L, LocalDate.now()), SubmissionStatus.UNDER_REVIEW, 1);

        when(submissionRepository.findWithGraphById(5L)).thenReturn(Optional.of(submission));
        when(submissionRepository.existsByIdAndAllocationSupervisorUserEmail(5L, GUIDE_EMAIL)).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.decide(GUIDE_EMAIL, 5L, SubmissionStatus.REVISION_REQUESTED, "  "));
    }

    @Test
    void approvingRecordsWhoDecidedAndWhen() {
        Submission submission = submission(allocation(AllocationStatus.ACCEPTED),
                milestone(3L, LocalDate.now()), SubmissionStatus.UNDER_REVIEW, 1);

        when(submissionRepository.findWithGraphById(5L)).thenReturn(Optional.of(submission));
        when(submissionRepository.existsByIdAndAllocationSupervisorUserEmail(5L, GUIDE_EMAIL)).thenReturn(true);
        when(userRepository.findByEmail(GUIDE_EMAIL)).thenReturn(Optional.of(user(7L, GUIDE_EMAIL, "Dr Test")));
        when(submissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Submission decided = service.decide(GUIDE_EMAIL, 5L, SubmissionStatus.APPROVED, null);

        assertEquals(SubmissionStatus.APPROVED, decided.getStatus());
        assertEquals("Dr Test", decided.getDecidedBy().getFullName());
        assertTrue(decided.getDecidedAt() != null, "an approval must be timestamped");
    }

    // ---- board --------------------------------------------------------------

    @Test
    void aStudentWithNoAllocationGetsAnEmptyBoard() {
        when(allocationService.currentAllocationFor(STUDENT_EMAIL)).thenReturn(Optional.empty());

        StudentSubmissionBoard board = service.boardFor(STUDENT_EMAIL);

        assertFalse(board.hasAllocation());
        assertTrue(board.rows().isEmpty());
    }

    @Test
    void theBoardListsEveryMilestoneIncludingUntouchedOnes() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        Milestone one = milestone(3L, LocalDate.now().plusDays(10));
        Milestone two = milestone(4L, LocalDate.now().plusDays(40));
        Submission filed = submission(allocation, one, SubmissionStatus.SUBMITTED, 1);

        when(allocationService.currentAllocationFor(STUDENT_EMAIL)).thenReturn(Optional.of(allocation));
        when(milestoneRepository.findBySessionOrderBySequenceNoAsc(allocation.getSession()))
                .thenReturn(List.of(one, two));
        when(submissionRepository.findByAllocationOrderByMilestoneSequenceNoAsc(allocation))
                .thenReturn(List.of(filed));
        when(versionRepository.findFirstBySubmissionOrderByVersionNoDesc(filed)).thenReturn(Optional.empty());

        StudentSubmissionBoard board = service.boardFor(STUDENT_EMAIL);

        assertTrue(board.hasAllocation());
        assertEquals(2, board.rows().size());
        assertTrue(board.rows().get(0).started());
        assertFalse(board.rows().get(1).started(), "an untouched milestone must still be listed");
        assertTrue(board.rows().get(1).acceptsUpload());
        assertEquals(1, board.submittedCount());
    }

    // ---- fixtures -----------------------------------------------------------

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "report.pdf", "application/pdf", "content".getBytes());
    }

    private StoredFile stored(String sha) {
        return new StoredFile("uploads/1/3/x.pdf", "report.pdf", "application/pdf", sha, 1234L);
    }

    private User user(Long id, String email, String name) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(name);
        return user;
    }

    private AcademicSession session() {
        AcademicSession session = new AcademicSession();
        session.setId(30L);
        session.setLabel("2025-26");
        session.setProgramme(Programme.MTECH);
        return session;
    }

    private Allocation allocation(AllocationStatus status) {
        StudentProfile student = new StudentProfile();
        student.setId(1L);
        student.setRollNo("24MCS001");
        student.setUser(user(1L, STUDENT_EMAIL, "Test Student"));

        SupervisorProfile guide = new SupervisorProfile();
        guide.setId(7L);
        guide.setUser(user(7L, GUIDE_EMAIL, "Dr Test"));

        Allocation allocation = new Allocation();
        allocation.setId(1L);
        allocation.setStudent(student);
        allocation.setSupervisor(guide);
        allocation.setSession(session());
        allocation.setStatus(status);
        return allocation;
    }

    private Milestone milestone(Long id, LocalDate dueDate) {
        Milestone milestone = new Milestone();
        milestone.setId(id);
        milestone.setName("Interim Report");
        milestone.setDueDate(dueDate);
        milestone.setWeightage(25);
        milestone.setSequenceNo(3);
        milestone.setSession(session());
        return milestone;
    }

    private Submission submission(Allocation allocation, Milestone milestone,
                                  SubmissionStatus status, int versionNo) {
        Submission submission = new Submission();
        submission.setId(5L);
        submission.setAllocation(allocation);
        submission.setMilestone(milestone);
        submission.setStatus(status);
        submission.setCurrentVersionNo(versionNo);
        return submission;
    }
}
