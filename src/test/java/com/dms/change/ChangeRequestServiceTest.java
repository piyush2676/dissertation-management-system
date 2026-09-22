package com.dms.change;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
import com.dms.allocation.AllocationService;
import com.dms.allocation.AllocationStatus;
import com.dms.audit.DomainEvents;
import com.dms.common.InvalidStateTransitionException;
import com.dms.session.AcademicSession;
import com.dms.topic.Topic;
import com.dms.topic.TopicRepository;
import com.dms.topic.TopicStatus;
import com.dms.user.Programme;
import com.dms.user.StudentProfile;
import com.dms.user.SupervisorProfile;
import com.dms.user.SupervisorProfileRepository;
import com.dms.user.User;
import com.dms.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * This service is the only door to two transitions the rest of the system
 * refuses, so these tests are as much about what it does not do as what it does.
 */
@ExtendWith(MockitoExtension.class)
class ChangeRequestServiceTest {

    private static final String STUDENT_EMAIL = "student@college.edu";
    private static final String COORDINATOR_EMAIL = "coordinator@college.edu";

    @Mock private ChangeRequestRepository requestRepository;
    @Mock private AllocationRepository allocationRepository;
    @Mock private AllocationService allocationService;
    @Mock private TopicRepository topicRepository;
    @Mock private SupervisorProfileRepository supervisorProfileRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher events;

    @InjectMocks private ChangeRequestService service;

    // ---- raising ------------------------------------------------------------

    @Test
    void aRequestLeavesTheAllocationExactlyAsItWas() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        givenLive(allocation);
        when(requestRepository.existsByAllocationAndStatus(allocation, ChangeRequestStatus.PENDING)).thenReturn(false);
        givenSaveEchoesItsArgument();

        ChangeRequest raised = service.raise(STUDENT_EMAIL, form(ChangeKind.SUPERVISOR, null));

        assertEquals(ChangeRequestStatus.PENDING, raised.getStatus());
        assertEquals(AllocationStatus.ACCEPTED, allocation.getStatus(),
                "section 4.11: the scholar keeps working while the committee considers it");
        verify(allocationRepository, never()).save(any());
        verify(events).publishEvent(any(DomainEvents.ChangeRequested.class));
    }

    @Test
    void aSecondRequestWhileOneIsPendingIsRefused() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        givenLive(allocation);
        when(requestRepository.existsByAllocationAndStatus(allocation, ChangeRequestStatus.PENDING)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.raise(STUDENT_EMAIL, form(ChangeKind.TITLE, "New title")));
        verify(requestRepository, never()).save(any());
    }

    @Test
    void namingTheGuideYouAlreadyHaveIsRefused() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        givenLive(allocation);
        when(requestRepository.existsByAllocationAndStatus(allocation, ChangeRequestStatus.PENDING)).thenReturn(false);
        when(supervisorProfileRepository.findById(7L)).thenReturn(Optional.of(allocation.getSupervisor()));
        ChangeRequestForm form = form(ChangeKind.SUPERVISOR, null);
        form.setPreferredSupervisorId(7L);

        assertThrows(IllegalArgumentException.class, () -> service.raise(STUDENT_EMAIL, form));
    }

    @Test
    void aStudentWithoutALivePlacementHasNothingToChange() {
        when(allocationService.currentAllocationFor(STUDENT_EMAIL))
                .thenReturn(Optional.of(allocation(AllocationStatus.REQUESTED)));

        assertThrows(IllegalStateException.class,
                () -> service.raise(STUDENT_EMAIL, form(ChangeKind.SUPERVISOR, null)));
    }

    // ---- approving a supervisor change --------------------------------------

    @Test
    void approvingASupervisorChangeWithdrawsThePlacement() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        ChangeRequest request = pending(allocation, ChangeKind.SUPERVISOR);
        givenDecidable(request);

        service.decide(COORDINATOR_EMAIL, 5L, decision(ChangeRequestStatus.APPROVED, null));

        assertEquals(AllocationStatus.WITHDRAWN, allocation.getStatus(),
                "the one transition the rest of the system refuses");
        assertNotNull(allocation.getDecidedAt());
        verify(allocationRepository).save(allocation);
    }

    @Test
    void aCoordinatorPlacementCanBeUndoneTheSameWay() {
        Allocation allocation = allocation(AllocationStatus.COORDINATOR_ASSIGNED);
        ChangeRequest request = pending(allocation, ChangeKind.SUPERVISOR);
        givenDecidable(request);

        service.decide(COORDINATOR_EMAIL, 5L, decision(ChangeRequestStatus.APPROVED, null));

        assertEquals(AllocationStatus.WITHDRAWN, allocation.getStatus(),
                "the mis-assignment this system could not undo before phase 16");
    }

    // ---- approving a title change -------------------------------------------

    @Test
    void approvingATitleChangeSendsTheApprovedTopicBackAndKeepsItsCode() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        Topic topic = topic(TopicStatus.APPROVED);
        allocation.setTopic(topic);
        ChangeRequest request = pending(allocation, ChangeKind.TITLE);
        givenDecidable(request);

        service.decide(COORDINATOR_EMAIL, 5L, decision(ChangeRequestStatus.APPROVED, null));

        assertEquals(TopicStatus.CHANGES_REQUESTED, topic.getStatus());
        assertEquals("MT26-001", topic.getThesisCode(), "Annexures 3 to 5 print this ID; it must survive");
        assertEquals(AllocationStatus.ACCEPTED, allocation.getStatus(), "a title change is not a guide change");
        verify(topicRepository).save(topic);
    }

    @Test
    void aTitleChangeOnATopicThatIsAlreadyBackWithTheStudentIsRefused() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        allocation.setTopic(topic(TopicStatus.CHANGES_REQUESTED));
        ChangeRequest request = pending(allocation, ChangeKind.TITLE);
        // Stubbed narrowly: the save is never reached, which is the point.
        when(requestRepository.findWithGraphById(5L)).thenReturn(Optional.of(request));
        when(userRepository.findByEmail(COORDINATOR_EMAIL))
                .thenReturn(Optional.of(user(2L, COORDINATOR_EMAIL, "PG Coordinator")));

        assertThrows(InvalidStateTransitionException.class,
                () -> service.decide(COORDINATOR_EMAIL, 5L, decision(ChangeRequestStatus.APPROVED, null)));
        verify(requestRepository, never()).save(any());
    }

    // ---- refusing and re-deciding -------------------------------------------

    @Test
    void refusingChangesNothingButTheRequest() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        Topic topic = topic(TopicStatus.APPROVED);
        allocation.setTopic(topic);
        ChangeRequest request = pending(allocation, ChangeKind.SUPERVISOR);
        givenDecidable(request);

        ChangeRequest decided = service.decide(COORDINATOR_EMAIL, 5L,
                decision(ChangeRequestStatus.REJECTED, "Work it out with your guide first."));

        assertEquals(ChangeRequestStatus.REJECTED, decided.getStatus());
        assertEquals("Work it out with your guide first.", decided.getDecisionNote());
        assertEquals(AllocationStatus.ACCEPTED, allocation.getStatus());
        assertEquals(TopicStatus.APPROVED, topic.getStatus());
        verify(allocationRepository, never()).save(any());
        verify(topicRepository, never()).save(any());
    }

    @Test
    void anAnsweredRequestCannotBeAnsweredAgain() {
        Allocation allocation = allocation(AllocationStatus.ACCEPTED);
        ChangeRequest request = pending(allocation, ChangeKind.SUPERVISOR);
        request.setStatus(ChangeRequestStatus.APPROVED);
        when(requestRepository.findWithGraphById(5L)).thenReturn(Optional.of(request));

        assertThrows(InvalidStateTransitionException.class,
                () -> service.decide(COORDINATOR_EMAIL, 5L, decision(ChangeRequestStatus.REJECTED, "no")));
        verify(allocationRepository, never()).save(any());
    }

    // ---- fixtures -----------------------------------------------------------

    private void givenLive(Allocation allocation) {
        when(allocationService.currentAllocationFor(STUDENT_EMAIL)).thenReturn(Optional.of(allocation));
        lenient().when(userRepository.findByEmail(STUDENT_EMAIL))
                .thenReturn(Optional.of(user(1L, STUDENT_EMAIL, "Test Student")));
    }

    private void givenDecidable(ChangeRequest request) {
        when(requestRepository.findWithGraphById(5L)).thenReturn(Optional.of(request));
        when(userRepository.findByEmail(COORDINATOR_EMAIL))
                .thenReturn(Optional.of(user(2L, COORDINATOR_EMAIL, "PG Coordinator")));
        when(requestRepository.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void givenSaveEchoesItsArgument() {
        when(requestRepository.save(any(ChangeRequest.class))).thenAnswer(inv -> {
            ChangeRequest saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(5L);
            }
            return saved;
        });
    }

    private static ChangeRequestForm form(ChangeKind kind, String proposedTitle) {
        ChangeRequestForm form = new ChangeRequestForm();
        form.setKind(kind);
        form.setReason("The domain has moved away from what I can realistically deliver this year.");
        form.setProposedTitle(proposedTitle);
        return form;
    }

    private static ChangeRequestDecisionForm decision(ChangeRequestStatus status, String note) {
        ChangeRequestDecisionForm form = new ChangeRequestDecisionForm();
        form.setDecision(status);
        form.setNote(note);
        return form;
    }

    private static ChangeRequest pending(Allocation allocation, ChangeKind kind) {
        ChangeRequest request = new ChangeRequest();
        request.setId(5L);
        request.setAllocation(allocation);
        request.setKind(kind);
        request.setReason("A reason the committee read.");
        request.setStatus(ChangeRequestStatus.PENDING);
        request.setRequestedBy(user(1L, STUDENT_EMAIL, "Test Student"));
        return request;
    }

    private static User user(Long id, String email, String name) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(name);
        return user;
    }

    private static Topic topic(TopicStatus status) {
        Topic topic = new Topic();
        topic.setId(10L);
        topic.setTitle("Adaptive load balancing");
        topic.setStatus(status);
        topic.setThesisCode("MT26-001");
        return topic;
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
}
