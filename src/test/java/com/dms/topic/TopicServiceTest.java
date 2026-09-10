package com.dms.topic;

import com.dms.common.InvalidStateTransitionException;
import com.dms.common.NotFoundException;
import com.dms.user.Programme;
import com.dms.user.StudentProfile;
import com.dms.user.StudentProfileRepository;
import com.dms.user.SupervisorProfile;
import com.dms.user.SupervisorProfileRepository;
import com.dms.user.User;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopicServiceTest {

    private static final String STUDENT_EMAIL = "student@college.edu";
    private static final String GUIDE_EMAIL = "guide@college.edu";
    private static final String OTHER_GUIDE_EMAIL = "other@college.edu";

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private StudentProfileRepository studentProfileRepository;

    @Mock
    private SupervisorProfileRepository supervisorProfileRepository;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private TopicService service;

    @Test
    void proposeFromDraftSetsProposedAndKeepsVersionOne() {
        StudentProfile student = student(1L, STUDENT_EMAIL);
        Topic topic = topic(10L, student, TopicStatus.DRAFT, 1);

        givenTopicOwnedByStudent(topic);
        givenSaveEchoesItsArgument();

        Topic result = service.propose(STUDENT_EMAIL, 10L);

        assertEquals(TopicStatus.PROPOSED, result.getStatus());
        assertEquals(1, result.getVersion(), "a first submission is still round one");
    }

    @Test
    void proposeFromChangesRequestedBumpsVersionAndClearsThePreviousDecision() {
        StudentProfile student = student(1L, STUDENT_EMAIL);
        Topic topic = topic(10L, student, TopicStatus.CHANGES_REQUESTED, 1);
        topic.setDecisionReason("narrow the scope");
        topic.setDecidedBy(user(7L, GUIDE_EMAIL, "Dr Sharma"));
        topic.setDecidedAt(Instant.now());

        givenTopicOwnedByStudent(topic);
        givenSaveEchoesItsArgument();

        Topic result = service.propose(STUDENT_EMAIL, 10L);

        assertEquals(TopicStatus.PROPOSED, result.getStatus());
        assertEquals(2, result.getVersion(), "a resubmission is a new round");

        assertNull(result.getDecisionReason());
        assertNull(result.getDecidedBy());
        assertNull(result.getDecidedAt());
    }

    @Test
    void proposeFromApprovedThrowsAndWritesNothing() {
        Topic topic = topic(10L, student(1L, STUDENT_EMAIL), TopicStatus.APPROVED, 1);
        givenTopicOwnedByStudent(topic);

        assertThrows(InvalidStateTransitionException.class,
                () -> service.propose(STUDENT_EMAIL, 10L));

        verify(topicRepository, never()).save(any());
    }

    @Test
    void proposeFromProposedThrows() {
        Topic topic = topic(10L, student(1L, STUDENT_EMAIL), TopicStatus.PROPOSED, 1);
        givenTopicOwnedByStudent(topic);

        assertThrows(InvalidStateTransitionException.class,
                () -> service.propose(STUDENT_EMAIL, 10L),
                "double submission must be refused");

        verify(topicRepository, never()).save(any());
    }

    @Test
    void proposeOnAnotherStudentsTopicThrowsNotFound() {
        Topic topic = topic(10L, student(2L, "someone.else@college.edu"), TopicStatus.DRAFT, 1);

        when(topicRepository.findById(10L)).thenReturn(Optional.of(topic));
        when(topicRepository.existsByIdAndStudentUserEmail(10L, STUDENT_EMAIL)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> service.propose(STUDENT_EMAIL, 10L));

        verify(topicRepository, never()).save(any());
    }

    @Test
    void decideApprovesAndStampsWhoAndWhen() {
        SupervisorProfile guide = supervisor(7L, GUIDE_EMAIL);
        Topic topic = topic(10L, student(1L, STUDENT_EMAIL), TopicStatus.PROPOSED, 1);
        topic.setProposedSupervisor(guide);

        when(topicRepository.findById(10L)).thenReturn(Optional.of(topic));
        when(supervisorProfileRepository.findByUserEmail(GUIDE_EMAIL)).thenReturn(Optional.of(guide));
        givenSaveEchoesItsArgument();

        Topic result = service.decide(GUIDE_EMAIL, 10L, decisionForm(TopicStatus.APPROVED, null));

        assertEquals(TopicStatus.APPROVED, result.getStatus());
        assertNull(result.getDecisionReason(), "an approval carries no reason");
        assertEquals(guide.getUser(), result.getDecidedBy());
        assertNotNull(result.getDecidedAt());
    }

    @Test
    void decideChangesRequestedStoresTheStrippedReason() {
        SupervisorProfile guide = supervisor(7L, GUIDE_EMAIL);
        Topic topic = topic(10L, student(1L, STUDENT_EMAIL), TopicStatus.PROPOSED, 1);
        topic.setProposedSupervisor(guide);

        when(topicRepository.findById(10L)).thenReturn(Optional.of(topic));
        when(supervisorProfileRepository.findByUserEmail(GUIDE_EMAIL)).thenReturn(Optional.of(guide));
        givenSaveEchoesItsArgument();

        Topic result = service.decide(GUIDE_EMAIL, 10L,
                decisionForm(TopicStatus.CHANGES_REQUESTED, "  narrow the scope  "));

        assertEquals(TopicStatus.CHANGES_REQUESTED, result.getStatus());
        assertEquals("narrow the scope", result.getDecisionReason());
        assertEquals(1, result.getVersion(), "the guide's decision does not open a new round");
    }

    @Test
    void decideByTheWrongSupervisorThrowsNotFound() {
        SupervisorProfile owner = supervisor(7L, GUIDE_EMAIL);
        SupervisorProfile intruder = supervisor(9L, OTHER_GUIDE_EMAIL);

        Topic topic = topic(10L, student(1L, STUDENT_EMAIL), TopicStatus.PROPOSED, 1);
        topic.setProposedSupervisor(owner);

        when(topicRepository.findById(10L)).thenReturn(Optional.of(topic));
        when(supervisorProfileRepository.findByUserEmail(OTHER_GUIDE_EMAIL)).thenReturn(Optional.of(intruder));

        assertThrows(NotFoundException.class,
                () -> service.decide(OTHER_GUIDE_EMAIL, 10L, decisionForm(TopicStatus.APPROVED, null)));

        verify(topicRepository, never()).save(any());
    }

    @Test
    void decideOnAnAlreadyDecidedTopicThrows() {
        SupervisorProfile guide = supervisor(7L, GUIDE_EMAIL);
        Topic topic = topic(10L, student(1L, STUDENT_EMAIL), TopicStatus.APPROVED, 1);
        topic.setProposedSupervisor(guide);

        when(topicRepository.findById(10L)).thenReturn(Optional.of(topic));
        when(supervisorProfileRepository.findByUserEmail(GUIDE_EMAIL)).thenReturn(Optional.of(guide));

        assertThrows(InvalidStateTransitionException.class,
                () -> service.decide(GUIDE_EMAIL, 10L, decisionForm(TopicStatus.REJECTED, "changed my mind")));

        verify(topicRepository, never()).save(any());
    }

    @Test
    void decideOnATopicWhoseSupervisorWasDeletedThrowsNotFound() {
        SupervisorProfile guide = supervisor(7L, GUIDE_EMAIL);
        Topic topic = topic(10L, student(1L, STUDENT_EMAIL), TopicStatus.PROPOSED, 1);
        topic.setProposedSupervisor(null);

        when(topicRepository.findById(10L)).thenReturn(Optional.of(topic));
        when(supervisorProfileRepository.findByUserEmail(GUIDE_EMAIL)).thenReturn(Optional.of(guide));

        assertThrows(NotFoundException.class,
                () -> service.decide(GUIDE_EMAIL, 10L, decisionForm(TopicStatus.APPROVED, null)),
                "a retired guide's topics must not blow up with a NullPointerException");
    }

    @Test
    void saveDraftWithNoIdCreatesADraftOwnedByTheCaller() {
        StudentProfile student = student(1L, STUDENT_EMAIL);
        SupervisorProfile guide = supervisor(7L, GUIDE_EMAIL);

        when(studentProfileRepository.findByUserEmail(STUDENT_EMAIL)).thenReturn(Optional.of(student));
        when(supervisorProfileRepository.findById(7L)).thenReturn(Optional.of(guide));
        givenSaveEchoesItsArgument();

        Topic result = service.saveDraft(STUDENT_EMAIL, topicForm(null, "Edge inference", 7L));

        assertEquals(TopicStatus.DRAFT, result.getStatus());
        assertEquals(student, result.getStudent());
        assertEquals(guide, result.getProposedSupervisor());
        assertEquals(1, result.getVersion());
    }

    @Test
    void saveDraftStripsWhitespaceFromTheTitle() {
        when(studentProfileRepository.findByUserEmail(STUDENT_EMAIL))
                .thenReturn(Optional.of(student(1L, STUDENT_EMAIL)));
        when(supervisorProfileRepository.findById(7L))
                .thenReturn(Optional.of(supervisor(7L, GUIDE_EMAIL)));
        givenSaveEchoesItsArgument();

        Topic result = service.saveDraft(STUDENT_EMAIL, topicForm(null, "   Edge inference   ", 7L));

        assertEquals("Edge inference", result.getTitle());
    }

    @Test
    void saveDraftOnATopicUnderReviewThrows() {
        StudentProfile student = student(1L, STUDENT_EMAIL);
        Topic topic = topic(10L, student, TopicStatus.PROPOSED, 1);

        when(studentProfileRepository.findByUserEmail(STUDENT_EMAIL)).thenReturn(Optional.of(student));
        givenTopicOwnedByStudent(topic);

        assertThrows(InvalidStateTransitionException.class,
                () -> service.saveDraft(STUDENT_EMAIL, topicForm(10L, "Rewritten", 7L)));

        verify(topicRepository, never()).save(any());
    }

    @Test
    void saveDraftWithAnUnknownSupervisorThrowsNotFound() {
        when(studentProfileRepository.findByUserEmail(STUDENT_EMAIL))
                .thenReturn(Optional.of(student(1L, STUDENT_EMAIL)));
        when(supervisorProfileRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.saveDraft(STUDENT_EMAIL, topicForm(null, "Edge inference", 99L)));

        verify(topicRepository, never()).save(any());
    }

    @Test
    void saveDraftForAnUnknownStudentThrowsNotFound() {
        when(studentProfileRepository.findByUserEmail(STUDENT_EMAIL)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.saveDraft(STUDENT_EMAIL, topicForm(null, "Edge inference", 7L)));

        verify(topicRepository, never()).save(any());
    }

    @Test
    void submitForApprovalSavesThenProposesInOneCall() {
        StudentProfile student = student(1L, STUDENT_EMAIL);

        Topic persistedDraft = topic(10L, student, TopicStatus.DRAFT, 1);

        when(studentProfileRepository.findByUserEmail(STUDENT_EMAIL)).thenReturn(Optional.of(student));
        when(supervisorProfileRepository.findById(7L)).thenReturn(Optional.of(supervisor(7L, GUIDE_EMAIL)));
        givenSaveEchoesItsArgument();
        givenTopicOwnedByStudent(persistedDraft);

        Topic result = service.submitForApproval(STUDENT_EMAIL, topicForm(null, "Edge inference", 7L));

        assertEquals(TopicStatus.PROPOSED, result.getStatus());
        assertEquals(1, result.getVersion());
    }

    private void givenTopicOwnedByStudent(Topic topic) {
        when(topicRepository.findById(topic.getId())).thenReturn(Optional.of(topic));
        when(topicRepository.existsByIdAndStudentUserEmail(topic.getId(), STUDENT_EMAIL)).thenReturn(true);
    }

    private void givenSaveEchoesItsArgument() {
        when(topicRepository.save(any(Topic.class))).thenAnswer(invocation -> {
            Topic saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(10L);
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

    private StudentProfile student(Long id, String email) {
        StudentProfile profile = new StudentProfile();
        profile.setId(id);
        profile.setUser(user(id, email, "Test Student"));
        profile.setRollNo("21INT0" + id);
        profile.setProgramme(Programme.MTECH);
        return profile;
    }

    private SupervisorProfile supervisor(Long id, String email) {
        SupervisorProfile profile = new SupervisorProfile();
        profile.setId(id);
        profile.setUser(user(id, email, "Dr Test"));
        profile.setDesignation("Professor");
        profile.setDepartment("CSE");
        profile.setMaxStudents(5);
        return profile;
    }

    private Topic topic(Long id, StudentProfile student, TopicStatus status, int version) {
        Topic topic = new Topic();
        topic.setId(id);
        topic.setStudent(student);
        topic.setStatus(status);
        topic.setVersion(version);
        topic.setTitle("Adaptive load balancing for edge inference clusters");
        topic.setAbstractText("A".repeat(200));
        return topic;
    }

    private TopicForm topicForm(Long id, String title, Long supervisorId) {
        TopicForm form = new TopicForm();
        form.setId(id);
        form.setTitle(title);
        form.setAbstractText("A".repeat(200));
        form.setKeywords("edge computing, scheduling");
        form.setProposedSupervisorId(supervisorId);
        return form;
    }

    private TopicDecisionForm decisionForm(TopicStatus decision, String reason) {
        TopicDecisionForm form = new TopicDecisionForm();
        form.setDecision(decision);
        form.setReason(reason);
        return form;
    }
}
