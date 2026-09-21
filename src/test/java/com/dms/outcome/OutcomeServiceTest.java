package com.dms.outcome;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationService;
import com.dms.allocation.AllocationStatus;
import com.dms.audit.DomainEvents;
import com.dms.common.NotFoundException;
import com.dms.session.AcademicSession;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutcomeServiceTest {

    private static final String STUDENT_EMAIL = "student@college.edu";
    private static final String COORDINATOR_EMAIL = "coordinator@college.edu";

    @Mock private OutcomeRepository outcomeRepository;
    @Mock private AllocationService allocationService;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher events;

    @InjectMocks private OutcomeService service;

    @Test
    void aReportedOutcomeStartsUnverified() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        when(allocationService.currentAllocationFor(STUDENT_EMAIL)).thenReturn(Optional.of(allocation));
        givenSaveEchoesItsArgument();

        Outcome outcome = service.report(STUDENT_EMAIL, form());

        assertFalse(outcome.isVerified());
        assertNull(outcome.getVerifiedAt());
        verify(events).publishEvent(any(DomainEvents.OutcomeReported.class));
    }

    @Test
    void aStudentWithoutALiveAllocationCannotReport() {
        when(allocationService.currentAllocationFor(STUDENT_EMAIL))
                .thenReturn(Optional.of(allocation(AllocationStatus.REQUESTED)));

        assertThrows(IllegalStateException.class, () -> service.report(STUDENT_EMAIL, form()));
        verify(outcomeRepository, never()).save(any());
    }

    @Test
    void editingAVerifiedOutcomeClearsTheVerification() {
        Outcome verified = outcome(5L, allocation(AllocationStatus.ACCEPTED));
        verified.setVerifiedBy(user(9L, COORDINATOR_EMAIL, "PG Coordinator"));
        verified.setVerifiedAt(Instant.now());
        verified.setVerificationNote("Seen on Scopus.");
        when(outcomeRepository.findById(5L)).thenReturn(Optional.of(verified));
        when(outcomeRepository.existsByIdAndAllocationStudentUserEmail(5L, STUDENT_EMAIL)).thenReturn(true);
        givenSaveEchoesItsArgument();

        Outcome result = service.revise(STUDENT_EMAIL, 5L, form());

        assertFalse(result.isVerified(), "the coordinator confirmed the old text, not the new one");
        assertNull(result.getVerifiedBy());
        assertNull(result.getVerificationNote());
    }

    @Test
    void aStudentCannotEditSomeoneElsesOutcome() {
        when(outcomeRepository.findById(5L)).thenReturn(Optional.of(outcome(5L, allocation(AllocationStatus.ACCEPTED))));
        when(outcomeRepository.existsByIdAndAllocationStudentUserEmail(5L, STUDENT_EMAIL)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> service.revise(STUDENT_EMAIL, 5L, form()));
    }

    @Test
    void verifyingStampsTheCoordinatorAndTheMoment() {
        Outcome outcome = outcome(5L, allocation(AllocationStatus.ACCEPTED));
        when(outcomeRepository.findWithGraphById(5L)).thenReturn(Optional.of(outcome));
        when(userRepository.findByEmail(COORDINATOR_EMAIL)).thenReturn(Optional.of(user(9L, COORDINATOR_EMAIL, "PG Coordinator")));
        givenSaveEchoesItsArgument();

        Outcome result = service.verify(COORDINATOR_EMAIL, 5L, verifyForm(true, "Seen on Scopus."));

        assertTrue(result.isVerified());
        assertEquals(COORDINATOR_EMAIL, result.getVerifiedBy().getEmail());
        assertNotNull(result.getVerifiedAt());
        ArgumentCaptor<DomainEvents.OutcomeVerified> published = ArgumentCaptor.forClass(DomainEvents.OutcomeVerified.class);
        verify(events).publishEvent(published.capture());
        assertEquals("OUTCOME_VERIFIED", published.getValue().action());
    }

    @Test
    void returningLeavesTheNoteAndNoVerification() {
        Outcome outcome = outcome(5L, allocation(AllocationStatus.ACCEPTED));
        when(outcomeRepository.findWithGraphById(5L)).thenReturn(Optional.of(outcome));
        when(userRepository.findByEmail(COORDINATOR_EMAIL)).thenReturn(Optional.of(user(9L, COORDINATOR_EMAIL, "PG Coordinator")));
        givenSaveEchoesItsArgument();

        Outcome result = service.verify(COORDINATOR_EMAIL, 5L, verifyForm(false, "No DOI resolves."));

        assertFalse(result.isVerified());
        assertEquals("No DOI resolves.", result.getVerificationNote());
    }

    @Test
    void onlyVerifiedAchievedRowsCountForTheRules() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        Outcome published = outcome(1L, allocation);
        published.setStatus(OutcomeStatus.PUBLISHED);
        published.setVerifiedBy(user(9L, COORDINATOR_EMAIL, "PG Coordinator"));
        published.setVerifiedAt(Instant.now());
        Outcome communicated = outcome(2L, allocation);
        communicated.setStatus(OutcomeStatus.COMMUNICATED);
        communicated.setVerifiedBy(user(9L, COORDINATOR_EMAIL, "PG Coordinator"));
        communicated.setVerifiedAt(Instant.now());
        when(outcomeRepository.findByAllocationAndVerifiedAtIsNotNullOrderByCreatedAtAsc(allocation))
                .thenReturn(List.of(published, communicated));

        List<OutcomeBoard.Row> counted = service.countedRowsFor(allocation);

        assertEquals(1, counted.size(), "a communicated paper is verified as reported but is not yet an outcome");
        assertEquals(1L, counted.get(0).id());
    }

    @Test
    void theSealedFactNamesTheVerifierNotTheStudent() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        Outcome published = outcome(1L, allocation);
        published.setStatus(OutcomeStatus.PUBLISHED);
        published.setReference("10.1000/xyz");
        published.setVerifiedBy(user(9L, COORDINATOR_EMAIL, "PG Coordinator"));
        published.setVerifiedAt(Instant.parse("2026-09-20T10:00:00Z"));
        when(outcomeRepository.findByAllocationAndVerifiedAtIsNotNullOrderByCreatedAtAsc(allocation))
                .thenReturn(List.of(published));

        List<String> facts = service.sealedFactsFor(allocation);

        assertEquals(List.of("JOURNAL_PAPER/SCOPUS/PUBLISHED/10.1000/xyz/" + COORDINATOR_EMAIL + "/2026-09-20T10:00:00Z"), facts);
    }

    // ---- fixtures -----------------------------------------------------------

    private void givenSaveEchoesItsArgument() {
        when(outcomeRepository.save(any(Outcome.class))).thenAnswer(inv -> {
            Outcome saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(42L);
            }
            return saved;
        });
    }

    private static OutcomeForm form() {
        OutcomeForm form = new OutcomeForm();
        form.setKind(OutcomeKind.JOURNAL_PAPER);
        form.setTitle("Energy-aware scheduling for edge inference");
        form.setVenue("IEEE Access");
        form.setIndexing(OutcomeIndexing.SCOPUS);
        form.setStatus(OutcomeStatus.ACCEPTED);
        form.setReference("10.1000/xyz");
        return form;
    }

    private static OutcomeVerifyForm verifyForm(boolean verified, String note) {
        OutcomeVerifyForm form = new OutcomeVerifyForm();
        form.setVerified(verified);
        form.setNote(note);
        return form;
    }

    private static User user(Long id, String email, String name) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(name);
        return user;
    }

    private static Allocation allocation(AllocationStatus status) {
        StudentProfile student = new StudentProfile();
        student.setId(1L);
        student.setRollNo("24MCS001");
        student.setProgramme(Programme.MTECH);
        student.setSemester(4);
        student.setUser(user(1L, STUDENT_EMAIL, "Test Student"));
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
        allocation.setStatus(status);
        return allocation;
    }

    private static Outcome outcome(Long id, Allocation allocation) {
        Outcome outcome = new Outcome();
        outcome.setId(id);
        outcome.setAllocation(allocation);
        outcome.setKind(OutcomeKind.JOURNAL_PAPER);
        outcome.setTitle("A paper");
        outcome.setIndexing(OutcomeIndexing.SCOPUS);
        outcome.setStatus(OutcomeStatus.ACCEPTED);
        return outcome;
    }
}
