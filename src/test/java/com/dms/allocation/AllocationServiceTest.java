package com.dms.allocation;

import com.dms.common.InvalidStateTransitionException;
import com.dms.common.NotFoundException;
import com.dms.session.AcademicSession;
import com.dms.session.AcademicSessionRepository;
import com.dms.topic.Topic;
import com.dms.topic.TopicRepository;
import com.dms.topic.TopicStatus;
import com.dms.user.Programme;
import com.dms.user.StudentProfile;
import com.dms.user.StudentProfileRepository;
import com.dms.user.SupervisorProfile;
import com.dms.user.SupervisorProfileRepository;
import com.dms.user.User;
import com.dms.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllocationServiceTest {

    private static final String STUDENT_EMAIL = "student@college.edu";
    private static final String GUIDE_EMAIL = "guide@college.edu";
    private static final String OTHER_GUIDE_EMAIL = "other@college.edu";
    private static final String COORDINATOR_EMAIL = "coordinator@college.edu";

    @Mock private AllocationRepository allocationRepository;
    @Mock private TopicRepository topicRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private SupervisorProfileRepository supervisorProfileRepository;
    @Mock private AcademicSessionRepository academicSessionRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private AllocationService service;

    @Test
    void requestCreatesARequestedAllocationAgainstTheApprovedTopic() {
        StudentProfile student = student(1L);
        SupervisorProfile guide = supervisor(7L, 5);
        AcademicSession session = session(30L);
        Topic topic = topic(10L, student, TopicStatus.APPROVED);

        givenStudentWithSession(student, session);
        when(topicRepository.findFirstByStudentOrderByCreatedAtDesc(student)).thenReturn(Optional.of(topic));
        when(allocationRepository.existsByStudentAndSessionAndStatusIn(
                student, session, AllocationStatus.LIVE)).thenReturn(false);
        when(supervisorProfileRepository.findById(7L)).thenReturn(Optional.of(guide));
        when(allocationRepository.countBySupervisorAndSessionAndStatusIn(
                guide, session, AllocationStatus.OCCUPIES_A_SEAT)).thenReturn(2L);
        givenSaveEchoesItsArgument();

        Allocation result = service.request(STUDENT_EMAIL, 7L);

        assertEquals(AllocationStatus.REQUESTED, result.getStatus());
        assertSame(student, result.getStudent());
        assertSame(guide, result.getSupervisor());
        assertSame(session, result.getSession());
        assertSame(topic, result.getTopic(), "the allocation records which topic was approved");
        assertNotNull(result.getRequestedAt());
        assertNull(result.getDecidedAt(), "nobody has answered yet");
        assertNull(result.getAllocatedBy());
    }

    @Test
    void requestWithoutAnApprovedTopicThrows() {
        StudentProfile student = student(1L);
        AcademicSession session = session(30L);

        givenStudentWithSession(student, session);
        when(topicRepository.findFirstByStudentOrderByCreatedAtDesc(student))
                .thenReturn(Optional.of(topic(10L, student, TopicStatus.PROPOSED)));

        assertThrows(IllegalStateException.class, () -> service.request(STUDENT_EMAIL, 7L));
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void requestWithNoTopicAtAllThrows() {
        StudentProfile student = student(1L);
        givenStudentWithSession(student, session(30L));
        when(topicRepository.findFirstByStudentOrderByCreatedAtDesc(student)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.request(STUDENT_EMAIL, 7L));
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void requestWithNoActiveSessionThrows() {
        StudentProfile student = student(1L);
        when(studentProfileRepository.findByUserEmail(STUDENT_EMAIL)).thenReturn(Optional.of(student));
        when(academicSessionRepository.findByProgrammeAndActiveTrue(Programme.BTECH)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.request(STUDENT_EMAIL, 7L));
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void requestWhenAlreadyAllocatedThrows() {
        StudentProfile student = student(1L);
        AcademicSession session = session(30L);

        givenStudentWithSession(student, session);
        when(topicRepository.findFirstByStudentOrderByCreatedAtDesc(student))
                .thenReturn(Optional.of(topic(10L, student, TopicStatus.APPROVED)));
        when(allocationRepository.existsByStudentAndSessionAndStatusIn(
                student, session, AllocationStatus.LIVE)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.request(STUDENT_EMAIL, 7L));
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void requestAgainstAFullSupervisorThrowsCapacity() {
        StudentProfile student = student(1L);
        SupervisorProfile guide = supervisor(7L, 5);
        AcademicSession session = session(30L);

        givenStudentWithSession(student, session);
        when(topicRepository.findFirstByStudentOrderByCreatedAtDesc(student))
                .thenReturn(Optional.of(topic(10L, student, TopicStatus.APPROVED)));
        when(allocationRepository.existsByStudentAndSessionAndStatusIn(
                student, session, AllocationStatus.LIVE)).thenReturn(false);
        when(supervisorProfileRepository.findById(7L)).thenReturn(Optional.of(guide));
        when(allocationRepository.countBySupervisorAndSessionAndStatusIn(
                guide, session, AllocationStatus.OCCUPIES_A_SEAT)).thenReturn(5L);

        CapacityExceededException thrown = assertThrows(CapacityExceededException.class,
                () -> service.request(STUDENT_EMAIL, 7L));

        assertEquals(5L, thrown.getTaken());
        assertEquals(5, thrown.getMax());
        assertEquals("Dr Test", thrown.getSupervisorName());
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void requestTranslatesAUniqueIndexClashIntoAReadableRefusal() {
        StudentProfile student = student(1L);
        SupervisorProfile guide = supervisor(7L, 5);
        AcademicSession session = session(30L);

        givenStudentWithSession(student, session);
        when(topicRepository.findFirstByStudentOrderByCreatedAtDesc(student))
                .thenReturn(Optional.of(topic(10L, student, TopicStatus.APPROVED)));
        when(allocationRepository.existsByStudentAndSessionAndStatusIn(
                student, session, AllocationStatus.LIVE)).thenReturn(false);
        when(supervisorProfileRepository.findById(7L)).thenReturn(Optional.of(guide));
        when(allocationRepository.countBySupervisorAndSessionAndStatusIn(
                guide, session, AllocationStatus.OCCUPIES_A_SEAT)).thenReturn(0L);
        when(allocationRepository.save(any(Allocation.class)))
                .thenThrow(new DataIntegrityViolationException("uq_allocations_one_live_per_student_session"));

        assertThrows(IllegalStateException.class, () -> service.request(STUDENT_EMAIL, 7L));
    }

    @Test
    void acceptStampsWhoAndWhen() {
        SupervisorProfile guide = supervisor(7L, 5);
        AcademicSession session = session(30L);
        Allocation allocation = allocation(50L, student(1L), guide, session, AllocationStatus.REQUESTED);

        givenAllocationOwnedBy(allocation, GUIDE_EMAIL);
        when(allocationRepository.countBySupervisorAndSessionAndStatusIn(
                guide, session, AllocationStatus.OCCUPIES_A_SEAT)).thenReturn(4L);
        givenSaveEchoesItsArgument();

        Allocation result = service.accept(GUIDE_EMAIL, 50L);

        assertEquals(AllocationStatus.ACCEPTED, result.getStatus());
        assertNotNull(result.getDecidedAt());
        assertSame(guide.getUser(), result.getAllocatedBy());
        assertNull(result.getDecisionReason(), "an acceptance carries no reason");
    }

    @Test
    void acceptOnTheLastSeatIsRefusedEvenThoughTheRequestWasAllowed() {
        SupervisorProfile guide = supervisor(7L, 5);
        AcademicSession session = session(30L);
        Allocation allocation = allocation(50L, student(1L), guide, session, AllocationStatus.REQUESTED);

        givenAllocationOwnedBy(allocation, GUIDE_EMAIL);
        when(allocationRepository.countBySupervisorAndSessionAndStatusIn(
                guide, session, AllocationStatus.OCCUPIES_A_SEAT)).thenReturn(5L);

        assertThrows(CapacityExceededException.class, () -> service.accept(GUIDE_EMAIL, 50L));
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void acceptByTheWrongSupervisorThrowsNotFound() {
        Allocation allocation =
                allocation(50L, student(1L), supervisor(7L, 5), session(30L), AllocationStatus.REQUESTED);

        when(allocationRepository.findById(50L)).thenReturn(Optional.of(allocation));
        when(allocationRepository.existsByIdAndSupervisorUserEmail(50L, OTHER_GUIDE_EMAIL)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> service.accept(OTHER_GUIDE_EMAIL, 50L));
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void acceptOnAnAlreadyAcceptedAllocationThrows() {
        Allocation allocation =
                allocation(50L, student(1L), supervisor(7L, 5), session(30L), AllocationStatus.ACCEPTED);

        givenAllocationOwnedBy(allocation, GUIDE_EMAIL);

        assertThrows(InvalidStateTransitionException.class, () -> service.accept(GUIDE_EMAIL, 50L));
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void declineStoresTheStrippedReason() {
        SupervisorProfile guide = supervisor(7L, 5);
        Allocation allocation = allocation(50L, student(1L), guide, session(30L), AllocationStatus.REQUESTED);

        givenAllocationOwnedBy(allocation, GUIDE_EMAIL);
        givenSaveEchoesItsArgument();

        Allocation result = service.decline(GUIDE_EMAIL, 50L, "  already at capacity next term  ");

        assertEquals(AllocationStatus.DECLINED, result.getStatus());
        assertEquals("already at capacity next term", result.getDecisionReason());
        assertNotNull(result.getDecidedAt());
        assertSame(guide.getUser(), result.getAllocatedBy());
    }

    @Test
    void declineWithoutAReasonThrows() {
        Allocation allocation =
                allocation(50L, student(1L), supervisor(7L, 5), session(30L), AllocationStatus.REQUESTED);

        givenAllocationOwnedBy(allocation, GUIDE_EMAIL);

        assertThrows(IllegalArgumentException.class, () -> service.decline(GUIDE_EMAIL, 50L, "   "));
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void declineAfterAcceptingThrows() {
        Allocation allocation =
                allocation(50L, student(1L), supervisor(7L, 5), session(30L), AllocationStatus.ACCEPTED);

        givenAllocationOwnedBy(allocation, GUIDE_EMAIL);

        assertThrows(InvalidStateTransitionException.class,
                () -> service.decline(GUIDE_EMAIL, 50L, "changed my mind"));
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void withdrawPullsBackAPendingRequest() {
        StudentProfile student = student(1L);
        Allocation allocation =
                allocation(50L, student, supervisor(7L, 5), session(30L), AllocationStatus.REQUESTED);

        when(allocationRepository.findById(50L)).thenReturn(Optional.of(allocation));
        when(allocationRepository.existsByIdAndStudentUserEmail(50L, STUDENT_EMAIL)).thenReturn(true);
        givenSaveEchoesItsArgument();

        Allocation result = service.withdraw(STUDENT_EMAIL, 50L);

        assertEquals(AllocationStatus.WITHDRAWN, result.getStatus());
        assertNotNull(result.getDecidedAt());
    }

    @Test
    void withdrawAfterTheGuideAcceptedThrows() {
        Allocation allocation =
                allocation(50L, student(1L), supervisor(7L, 5), session(30L), AllocationStatus.ACCEPTED);

        when(allocationRepository.findById(50L)).thenReturn(Optional.of(allocation));
        when(allocationRepository.existsByIdAndStudentUserEmail(50L, STUDENT_EMAIL)).thenReturn(true);

        assertThrows(InvalidStateTransitionException.class, () -> service.withdraw(STUDENT_EMAIL, 50L));
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void withdrawSomeoneElsesAllocationThrowsNotFound() {
        Allocation allocation =
                allocation(50L, student(2L), supervisor(7L, 5), session(30L), AllocationStatus.REQUESTED);

        when(allocationRepository.findById(50L)).thenReturn(Optional.of(allocation));
        when(allocationRepository.existsByIdAndStudentUserEmail(50L, STUDENT_EMAIL)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> service.withdraw(STUDENT_EMAIL, 50L));
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void assignPlacesTheStudentWithoutARequest() {
        StudentProfile student = student(1L);
        SupervisorProfile guide = supervisor(7L, 5);
        AcademicSession session = session(30L);
        User coordinator = user(99L, COORDINATOR_EMAIL, "PG Coordinator");
        Topic topic = topic(10L, student, TopicStatus.APPROVED);

        when(userRepository.findByEmail(COORDINATOR_EMAIL)).thenReturn(Optional.of(coordinator));
        when(studentProfileRepository.findById(1L)).thenReturn(Optional.of(student));
        when(academicSessionRepository.findByProgrammeAndActiveTrue(Programme.BTECH)).thenReturn(Optional.of(session));
        when(supervisorProfileRepository.findById(7L)).thenReturn(Optional.of(guide));
        when(allocationRepository.existsByStudentAndSessionAndStatusIn(
                student, session, AllocationStatus.LIVE)).thenReturn(false);
        when(allocationRepository.countBySupervisorAndSessionAndStatusIn(
                guide, session, AllocationStatus.OCCUPIES_A_SEAT)).thenReturn(1L);
        when(topicRepository.findFirstByStudentOrderByCreatedAtDesc(student)).thenReturn(Optional.of(topic));
        givenSaveEchoesItsArgument();

        Allocation result = service.assign(COORDINATOR_EMAIL, 1L, 7L);

        assertEquals(AllocationStatus.COORDINATOR_ASSIGNED, result.getStatus());
        assertSame(coordinator, result.getAllocatedBy(), "the override records who made it");
        assertEquals(result.getRequestedAt(), result.getDecidedAt(),
                "there was no request, so both instants are the same moment");
    }

    @Test
    void assignLeavesTheTopicNullWhenNothingIsApprovedYet() {
        StudentProfile student = student(1L);
        SupervisorProfile guide = supervisor(7L, 5);
        AcademicSession session = session(30L);

        when(userRepository.findByEmail(COORDINATOR_EMAIL))
                .thenReturn(Optional.of(user(99L, COORDINATOR_EMAIL, "PG Coordinator")));
        when(studentProfileRepository.findById(1L)).thenReturn(Optional.of(student));
        when(academicSessionRepository.findByProgrammeAndActiveTrue(Programme.BTECH)).thenReturn(Optional.of(session));
        when(supervisorProfileRepository.findById(7L)).thenReturn(Optional.of(guide));
        when(allocationRepository.existsByStudentAndSessionAndStatusIn(
                student, session, AllocationStatus.LIVE)).thenReturn(false);
        when(allocationRepository.countBySupervisorAndSessionAndStatusIn(
                guide, session, AllocationStatus.OCCUPIES_A_SEAT)).thenReturn(0L);
        when(topicRepository.findFirstByStudentOrderByCreatedAtDesc(student))
                .thenReturn(Optional.of(topic(10L, student, TopicStatus.CHANGES_REQUESTED)));
        givenSaveEchoesItsArgument();

        Allocation result = service.assign(COORDINATOR_EMAIL, 1L, 7L);

        assertNull(result.getTopic(), "only an approved topic is attached");
        assertEquals(AllocationStatus.COORDINATOR_ASSIGNED, result.getStatus());
    }

    @Test
    void assignDoesNotOverrideCapacity() {
        StudentProfile student = student(1L);
        SupervisorProfile guide = supervisor(7L, 5);
        AcademicSession session = session(30L);

        when(userRepository.findByEmail(COORDINATOR_EMAIL))
                .thenReturn(Optional.of(user(99L, COORDINATOR_EMAIL, "PG Coordinator")));
        when(studentProfileRepository.findById(1L)).thenReturn(Optional.of(student));
        when(academicSessionRepository.findByProgrammeAndActiveTrue(Programme.BTECH)).thenReturn(Optional.of(session));
        when(supervisorProfileRepository.findById(7L)).thenReturn(Optional.of(guide));
        when(allocationRepository.existsByStudentAndSessionAndStatusIn(
                student, session, AllocationStatus.LIVE)).thenReturn(false);
        when(allocationRepository.countBySupervisorAndSessionAndStatusIn(
                guide, session, AllocationStatus.OCCUPIES_A_SEAT)).thenReturn(5L);

        assertThrows(CapacityExceededException.class, () -> service.assign(COORDINATOR_EMAIL, 1L, 7L));
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void assignOnTopOfALiveAllocationThrows() {
        StudentProfile student = student(1L);
        AcademicSession session = session(30L);

        when(userRepository.findByEmail(COORDINATOR_EMAIL))
                .thenReturn(Optional.of(user(99L, COORDINATOR_EMAIL, "PG Coordinator")));
        when(studentProfileRepository.findById(1L)).thenReturn(Optional.of(student));
        when(academicSessionRepository.findByProgrammeAndActiveTrue(Programme.BTECH)).thenReturn(Optional.of(session));
        when(supervisorProfileRepository.findById(7L)).thenReturn(Optional.of(supervisor(7L, 5)));
        when(allocationRepository.existsByStudentAndSessionAndStatusIn(
                student, session, AllocationStatus.LIVE)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.assign(COORDINATOR_EMAIL, 1L, 7L));
        verify(allocationRepository, never()).save(any());
    }

    private void givenStudentWithSession(StudentProfile student, AcademicSession session) {
        when(studentProfileRepository.findByUserEmail(STUDENT_EMAIL)).thenReturn(Optional.of(student));
        when(academicSessionRepository.findByProgrammeAndActiveTrue(eq(Programme.BTECH)))
                .thenReturn(Optional.of(session));
    }

    private void givenAllocationOwnedBy(Allocation allocation, String supervisorEmail) {
        when(allocationRepository.findById(allocation.getId())).thenReturn(Optional.of(allocation));
        when(allocationRepository.existsByIdAndSupervisorUserEmail(allocation.getId(), supervisorEmail))
                .thenReturn(true);
    }

    private void givenSaveEchoesItsArgument() {
        when(allocationRepository.save(any(Allocation.class))).thenAnswer(invocation -> {
            Allocation saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(50L);
            }
            return saved;
        });
    }

    private User user(Long id, String email, String fullName) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(fullName);
        return user;
    }

    private StudentProfile student(Long id) {
        StudentProfile profile = new StudentProfile();
        profile.setId(id);
        profile.setUser(user(id, STUDENT_EMAIL, "Test Student"));
        profile.setRollNo("21INT00" + id);
        profile.setProgramme(Programme.BTECH);
        return profile;
    }

    private SupervisorProfile supervisor(Long id, int maxStudents) {
        SupervisorProfile profile = new SupervisorProfile();
        profile.setId(id);
        profile.setUser(user(id, GUIDE_EMAIL, "Dr Test"));
        profile.setDesignation("Professor");
        profile.setDepartment("CSE");
        profile.setMaxStudents(maxStudents);
        return profile;
    }

    private AcademicSession session(Long id) {
        AcademicSession session = new AcademicSession();
        session.setId(id);
        session.setLabel("2025-26");
        session.setProgramme(Programme.BTECH);
        session.setStartDate(LocalDate.of(2025, 7, 1));
        session.setEndDate(LocalDate.of(2026, 5, 31));
        session.setActive(true);
        return session;
    }

    private Topic topic(Long id, StudentProfile student, TopicStatus status) {
        Topic topic = new Topic();
        topic.setId(id);
        topic.setStudent(student);
        topic.setStatus(status);
        topic.setTitle("Adaptive load balancing for edge inference clusters");
        topic.setAbstractText("A".repeat(200));
        return topic;
    }

    private Allocation allocation(Long id, StudentProfile student, SupervisorProfile supervisor,
                                  AcademicSession session, AllocationStatus status) {
        Allocation allocation = new Allocation();
        allocation.setId(id);
        allocation.setStudent(student);
        allocation.setSupervisor(supervisor);
        allocation.setSession(session);
        allocation.setStatus(status);
        return allocation;
    }
}
