package com.dms.logbook;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
import com.dms.allocation.AllocationService;
import com.dms.allocation.AllocationStatus;
import com.dms.audit.DomainEvents;
import com.dms.common.InvalidStateTransitionException;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogbookServiceTest {

    private static final String STUDENT_EMAIL = "student@college.edu";
    private static final String GUIDE_EMAIL = "guide@college.edu";
    private static final String OTHER_GUIDE_EMAIL = "other@college.edu";

    @Mock private LogbookEntryRepository entryRepository;
    @Mock private AllocationRepository allocationRepository;
    @Mock private AllocationService allocationService;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher events;

    @InjectMocks private LogbookService service;

    // ---- recording ----------------------------------------------------------

    @Test
    void theFirstMeetingIsNumberedOne() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        when(allocationService.currentAllocationFor(STUDENT_EMAIL)).thenReturn(Optional.of(allocation));
        when(entryRepository.findFirstByAllocationOrderByMeetingNoDesc(allocation)).thenReturn(Optional.empty());
        givenSaveEchoesItsArgument();

        LogbookEntry entry = service.record(STUDENT_EMAIL, form());

        assertEquals(1, entry.getMeetingNo());
        assertEquals(LogbookEntryStatus.PENDING, entry.getStatus());
        assertNull(entry.getEntryDigest(), "nothing is sealed until the guide signs");
    }

    @Test
    void meetingsAreNumberedAfterTheLastOneTheStudentNeverPicks() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        LogbookEntry last = entry(5L, allocation, 4, LogbookEntryStatus.SIGNED);
        when(allocationService.currentAllocationFor(STUDENT_EMAIL)).thenReturn(Optional.of(allocation));
        when(entryRepository.findFirstByAllocationOrderByMeetingNoDesc(allocation)).thenReturn(Optional.of(last));
        givenSaveEchoesItsArgument();

        assertEquals(5, service.record(STUDENT_EMAIL, form()).getMeetingNo());
    }

    @Test
    void recordingPublishesAnEventForTheGuide() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        when(allocationService.currentAllocationFor(STUDENT_EMAIL)).thenReturn(Optional.of(allocation));
        when(entryRepository.findFirstByAllocationOrderByMeetingNoDesc(allocation)).thenReturn(Optional.empty());
        givenSaveEchoesItsArgument();

        service.record(STUDENT_EMAIL, form());

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        assertTrue(published.getValue() instanceof DomainEvents.LogbookEntryRecorded);
    }

    @Test
    void aStudentWithoutALiveAllocationCannotRecordAMeeting() {
        when(allocationService.currentAllocationFor(STUDENT_EMAIL))
                .thenReturn(Optional.of(allocation(AllocationStatus.REQUESTED)));

        assertThrows(IllegalStateException.class, () -> service.record(STUDENT_EMAIL, form()));
        verify(entryRepository, never()).save(any());
    }

    @Test
    void aBlankChallengesFieldIsStoredAsNull() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        when(allocationService.currentAllocationFor(STUDENT_EMAIL)).thenReturn(Optional.of(allocation));
        when(entryRepository.findFirstByAllocationOrderByMeetingNoDesc(allocation)).thenReturn(Optional.empty());
        givenSaveEchoesItsArgument();
        LogbookEntryForm form = form();
        form.setChallenges("   ");

        assertNull(service.record(STUDENT_EMAIL, form).getChallenges());
    }

    // ---- revising -----------------------------------------------------------

    @Test
    void aSignedRowCannotBeEditedThatIsWhatSealedMeans() {
        LogbookEntry signed = entry(9L, allocation(AllocationStatus.ACCEPTED), 2, LogbookEntryStatus.SIGNED);
        givenOwnedByStudent(signed);

        assertThrows(InvalidStateTransitionException.class,
                () -> service.revise(STUDENT_EMAIL, 9L, form()));
        verify(entryRepository, never()).save(any());
    }

    @Test
    void correctingAReturnedRowSendsItBackToTheGuideAndClearsTheirRemark() {
        LogbookEntry returned = entry(9L, allocation(AllocationStatus.ACCEPTED), 2, LogbookEntryStatus.RETURNED);
        returned.setSupervisorRemarks("Name the dataset.");
        givenOwnedByStudent(returned);
        givenSaveEchoesItsArgument();

        LogbookEntry result = service.revise(STUDENT_EMAIL, 9L, form());

        assertEquals(LogbookEntryStatus.PENDING, result.getStatus());
        assertNull(result.getSupervisorRemarks(), "the old remark belonged to the old text");
        verify(events).publishEvent(any(DomainEvents.LogbookEntryRecorded.class));
    }

    @Test
    void editingAPendingRowDoesNotReNotifyTheGuide() {
        LogbookEntry pending = entry(9L, allocation(AllocationStatus.ACCEPTED), 2, LogbookEntryStatus.PENDING);
        givenOwnedByStudent(pending);
        givenSaveEchoesItsArgument();

        service.revise(STUDENT_EMAIL, 9L, form());

        verify(events, never()).publishEvent(any());
    }

    @Test
    void aStudentCannotEditSomeoneElsesRow() {
        LogbookEntry pending = entry(9L, allocation(AllocationStatus.ACCEPTED), 2, LogbookEntryStatus.PENDING);
        when(entryRepository.findById(9L)).thenReturn(Optional.of(pending));
        when(entryRepository.existsByIdAndAllocationStudentUserEmail(9L, STUDENT_EMAIL)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> service.revise(STUDENT_EMAIL, 9L, form()));
    }

    // ---- signing ------------------------------------------------------------

    @Test
    void signingSealsTheRowWithADigestAndTheSigner() {
        LogbookEntry pending = entry(9L, allocation(AllocationStatus.ACCEPTED), 2, LogbookEntryStatus.PENDING);
        givenOwnedByGuide(pending);
        givenSaveEchoesItsArgument();

        LogbookEntry result = service.decide(GUIDE_EMAIL, 9L, sign(LogbookEntryStatus.SIGNED, "Good week."));

        assertEquals(LogbookEntryStatus.SIGNED, result.getStatus());
        assertEquals(64, result.getEntryDigest().length(), "a SHA-256 hex");
        assertEquals(GUIDE_EMAIL, result.getSignedBy().getEmail());
        assertNotNull(result.getSignedAt());
        assertEquals("Good week.", result.getSupervisorRemarks());
    }

    @Test
    void theSignedEventCarriesTheDigestSoTheAuditTrailPinsIt() {
        LogbookEntry pending = entry(9L, allocation(AllocationStatus.ACCEPTED), 2, LogbookEntryStatus.PENDING);
        givenOwnedByGuide(pending);
        givenSaveEchoesItsArgument();

        LogbookEntry result = service.decide(GUIDE_EMAIL, 9L, sign(LogbookEntryStatus.SIGNED, null));

        ArgumentCaptor<DomainEvents.LogbookEntryDecided> published =
                ArgumentCaptor.forClass(DomainEvents.LogbookEntryDecided.class);
        verify(events).publishEvent(published.capture());
        assertEquals(result.getEntryDigest(), published.getValue().digest());
        assertEquals("LOGBOOK_SIGNED", published.getValue().action());
    }

    @Test
    void returningLeavesNoDigestAndNoSignature() {
        LogbookEntry pending = entry(9L, allocation(AllocationStatus.ACCEPTED), 2, LogbookEntryStatus.PENDING);
        givenOwnedByGuide(pending);
        givenSaveEchoesItsArgument();

        LogbookEntry result = service.decide(GUIDE_EMAIL, 9L, sign(LogbookEntryStatus.RETURNED, "Name the dataset."));

        assertEquals(LogbookEntryStatus.RETURNED, result.getStatus());
        assertNull(result.getEntryDigest());
        assertNull(result.getSignedAt());
        assertEquals("Name the dataset.", result.getSupervisorRemarks());
    }

    @Test
    void aSignedRowCannotBeSignedAgainOrReturned() {
        LogbookEntry signed = entry(9L, allocation(AllocationStatus.ACCEPTED), 2, LogbookEntryStatus.SIGNED);
        givenOwnedByGuide(signed);

        assertThrows(InvalidStateTransitionException.class,
                () -> service.decide(GUIDE_EMAIL, 9L, sign(LogbookEntryStatus.RETURNED, "too late")));
        verify(entryRepository, never()).save(any());
    }

    @Test
    void anotherGuideCannotSignSomeoneElsesStudent() {
        LogbookEntry pending = entry(9L, allocation(AllocationStatus.ACCEPTED), 2, LogbookEntryStatus.PENDING);
        when(entryRepository.findWithGraphById(9L)).thenReturn(Optional.of(pending));
        when(entryRepository.existsByIdAndAllocationSupervisorUserEmail(9L, OTHER_GUIDE_EMAIL)).thenReturn(false);

        assertThrows(NotFoundException.class,
                () -> service.decide(OTHER_GUIDE_EMAIL, 9L, sign(LogbookEntryStatus.SIGNED, null)));
    }

    // ---- the seal -----------------------------------------------------------

    @Test
    void theDigestIsStableForTheSameRow() {
        LogbookEntry entry = signedEntry();
        assertEquals(LogbookService.digestOf(entry, GUIDE_EMAIL), LogbookService.digestOf(entry, GUIDE_EMAIL));
    }

    @Test
    void changingWhatWasCompletedChangesTheDigest() {
        LogbookEntry entry = signedEntry();
        String before = LogbookService.digestOf(entry, GUIDE_EMAIL);
        entry.setWorkCompleted("Something else entirely");
        assertNotEquals(before, LogbookService.digestOf(entry, GUIDE_EMAIL),
                "an edited signed row must stop matching");
    }

    @Test
    void changingTheSignerChangesTheDigest() {
        LogbookEntry entry = signedEntry();
        assertNotEquals(LogbookService.digestOf(entry, GUIDE_EMAIL), LogbookService.digestOf(entry, OTHER_GUIDE_EMAIL));
    }

    @Test
    void onlySignedRowsReachTheCertificate() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        LogbookEntry signed = entry(1L, allocation, 1, LogbookEntryStatus.SIGNED);
        signed.setEntryDigest("ab".repeat(32));
        when(entryRepository.findByAllocationAndStatusOrderByMeetingNoAsc(allocation, LogbookEntryStatus.SIGNED))
                .thenReturn(List.of(signed));

        assertEquals(List.of("1:" + "ab".repeat(32)), service.sealedFactsFor(allocation));
    }

    // ---- fixtures -----------------------------------------------------------

    private void givenOwnedByStudent(LogbookEntry entry) {
        when(entryRepository.findById(entry.getId())).thenReturn(Optional.of(entry));
        when(entryRepository.existsByIdAndAllocationStudentUserEmail(entry.getId(), STUDENT_EMAIL)).thenReturn(true);
    }

    private void givenOwnedByGuide(LogbookEntry entry) {
        when(entryRepository.findWithGraphById(entry.getId())).thenReturn(Optional.of(entry));
        when(entryRepository.existsByIdAndAllocationSupervisorUserEmail(entry.getId(), GUIDE_EMAIL)).thenReturn(true);
        lenient().when(userRepository.findByEmail(GUIDE_EMAIL))
                .thenReturn(Optional.of(user(7L, GUIDE_EMAIL, "Dr Test")));
    }

    private void givenSaveEchoesItsArgument() {
        when(entryRepository.save(any(LogbookEntry.class))).thenAnswer(inv -> {
            LogbookEntry saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(42L);
            }
            return saved;
        });
    }

    private static LogbookEntryForm form() {
        LogbookEntryForm form = new LogbookEntryForm();
        form.setMeetingAt(LocalDateTime.now().minusHours(2));
        form.setWorkAssigned("Read the three survey papers.");
        form.setWorkCompleted("Summarised two; the third is behind a paywall.");
        form.setChallenges("Library access.");
        return form;
    }

    private static LogbookSignForm sign(LogbookEntryStatus decision, String remarks) {
        LogbookSignForm form = new LogbookSignForm();
        form.setDecision(decision);
        form.setRemarks(remarks);
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
        guide.setUser(user(7L, GUIDE_EMAIL, "Dr Test"));

        AcademicSession session = new AcademicSession();
        session.setId(30L);
        session.setLabel("2026-27");
        session.setProgramme(Programme.MTECH);

        Allocation allocation = new Allocation();
        allocation.setId(11L);
        allocation.setStudent(student);
        allocation.setSupervisor(guide);
        allocation.setSession(session);
        allocation.setStatus(status);
        return allocation;
    }

    private static LogbookEntry entry(Long id, Allocation allocation, int meetingNo, LogbookEntryStatus status) {
        LogbookEntry entry = new LogbookEntry();
        entry.setId(id);
        entry.setAllocation(allocation);
        entry.setMeetingNo(meetingNo);
        entry.setMeetingAt(Instant.parse("2026-09-10T09:30:00Z"));
        entry.setWorkAssigned("Read the three survey papers.");
        entry.setWorkCompleted("Summarised two.");
        entry.setStatus(status);
        return entry;
    }

    private static LogbookEntry signedEntry() {
        LogbookEntry entry = entry(9L, allocation(AllocationStatus.ACCEPTED), 2, LogbookEntryStatus.SIGNED);
        entry.setSupervisorRemarks("Fine.");
        entry.setSignedAt(Instant.parse("2026-09-11T10:00:00Z"));
        return entry;
    }
}
