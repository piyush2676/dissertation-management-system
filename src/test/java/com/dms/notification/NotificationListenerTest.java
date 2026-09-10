package com.dms.notification;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
import com.dms.audit.DomainEvents;
import com.dms.session.Milestone;
import com.dms.submission.Submission;
import com.dms.submission.SubmissionRepository;
import com.dms.topic.Topic;
import com.dms.topic.TopicRepository;
import com.dms.user.StudentProfile;
import com.dms.user.SupervisorProfile;
import com.dms.user.User;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The rule under test throughout: a notification goes to whoever now has to act,
 * never back to the person who just acted.
 */
@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    private static final String STUDENT_EMAIL = "student@college.edu";
    private static final String GUIDE_EMAIL = "guide@college.edu";
    private static final String COORDINATOR_EMAIL = "coordinator@college.edu";

    @Mock private NotificationService notifications;
    @Mock private TopicRepository topicRepository;
    @Mock private AllocationRepository allocationRepository;
    @Mock private SubmissionRepository submissionRepository;

    @InjectMocks private NotificationListener listener;

    @Test
    void aProposedTopicNotifiesTheNamedGuideNotTheStudent() {
        when(topicRepository.findWithGraphById(1L)).thenReturn(Optional.of(topic()));

        listener.on(new DomainEvents.TopicProposed(STUDENT_EMAIL, 1L, "Edge inference"));

        assertEquals(GUIDE_EMAIL, capturedRecipient().getEmail());
    }

    @Test
    void aTopicWithNoNamedGuideNotifiesNobody() {
        Topic topic = topic();
        topic.setProposedSupervisor(null);
        when(topicRepository.findWithGraphById(1L)).thenReturn(Optional.of(topic));

        listener.on(new DomainEvents.TopicProposed(STUDENT_EMAIL, 1L, "Edge inference"));

        verify(notifications, never()).notify(any(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void aTopicDecisionNotifiesTheStudentAndReadsAsProse() {
        when(topicRepository.findWithGraphById(1L)).thenReturn(Optional.of(topic()));

        listener.on(new DomainEvents.TopicDecided(GUIDE_EMAIL, 1L, "PROPOSED", "CHANGES_REQUESTED"));

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(notifications).notify(any(), eq(NotificationType.TOPIC_DECIDED),
                title.capture(), anyString(), anyString());
        assertEquals("Your topic was changes requested", title.getValue(),
                "a screaming-snake status does not belong in a headline");
    }

    @Test
    void aGuideRequestNotifiesTheGuide() {
        when(allocationRepository.findWithGraphById(2L)).thenReturn(Optional.of(allocation()));

        listener.on(new DomainEvents.GuideRequested(STUDENT_EMAIL, 2L, "Dr Test"));

        assertEquals(GUIDE_EMAIL, capturedRecipient().getEmail());
    }

    @Test
    void aGuideDecisionNotifiesTheStudent() {
        when(allocationRepository.findWithGraphById(2L)).thenReturn(Optional.of(allocation()));

        listener.on(new DomainEvents.GuideDecided(GUIDE_EMAIL, 2L, "REQUESTED", "ACCEPTED"));

        assertEquals(STUDENT_EMAIL, capturedRecipient().getEmail());
    }

    @Test
    void aWithdrawalNotifiesTheGuideBecauseTheStudentIsTheOneWhoActed() {
        when(allocationRepository.findWithGraphById(2L)).thenReturn(Optional.of(allocation()));

        listener.on(new DomainEvents.GuideDecided(STUDENT_EMAIL, 2L, "REQUESTED", "WITHDRAWN"));

        assertEquals(GUIDE_EMAIL, capturedRecipient().getEmail());
    }

    @Test
    void aCoordinatorOverrideNotifiesBothSidesSinceNeitherActed() {
        when(allocationRepository.findWithGraphById(2L)).thenReturn(Optional.of(allocation()));

        listener.on(new DomainEvents.GuideAssigned(COORDINATOR_EMAIL, 2L, "Dr Test"));

        ArgumentCaptor<User> recipients = ArgumentCaptor.forClass(User.class);
        verify(notifications, times(2)).notify(recipients.capture(),
                eq(NotificationType.GUIDE_ASSIGNED), anyString(), anyString(), anyString());

        assertTrue(recipients.getAllValues().stream()
                .map(User::getEmail).toList().containsAll(java.util.List.of(STUDENT_EMAIL, GUIDE_EMAIL)));
    }

    @Test
    void filedWorkNotifiesTheGuideAndLinksToTheSubmission() {
        when(submissionRepository.findWithGraphById(5L)).thenReturn(Optional.of(submission()));

        listener.on(new DomainEvents.SubmissionFiled(STUDENT_EMAIL, 5L, "Synopsis", 1));

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(notifications).notify(any(), eq(NotificationType.SUBMISSION_FILED),
                anyString(), anyString(), link.capture());
        assertEquals("/supervisor/submissions/5", link.getValue());
    }

    @Test
    void aReviewStartingNotifiesTheStudent() {
        when(submissionRepository.findWithGraphById(5L)).thenReturn(Optional.of(submission()));

        listener.on(new DomainEvents.SubmissionReviewStarted(GUIDE_EMAIL, 5L, "Synopsis"));

        assertEquals(STUDENT_EMAIL, capturedRecipient().getEmail());
    }

    @Test
    void aCommentNotifiesTheStudentWhoseWorkItIsOn() {
        when(submissionRepository.findWithGraphById(5L)).thenReturn(Optional.of(submission()));

        listener.on(new DomainEvents.ReviewCommented(GUIDE_EMAIL, 5L, "Add a baseline."));

        assertEquals(STUDENT_EMAIL, capturedRecipient().getEmail());
    }

    @Test
    void aMissingRecordIsIgnoredRatherThanThrowing() {
        when(submissionRepository.findWithGraphById(5L)).thenReturn(Optional.empty());

        listener.on(new DomainEvents.SubmissionFiled(STUDENT_EMAIL, 5L, "Synopsis", 1));

        verify(notifications, never()).notify(any(), any(), anyString(), anyString(), anyString());
    }

    // ---- fixtures -----------------------------------------------------------

    private User capturedRecipient() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(notifications).notify(captor.capture(), any(), anyString(), anyString(), anyString());
        return captor.getValue();
    }

    private User user(String email, String name) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(name);
        return user;
    }

    private StudentProfile student() {
        StudentProfile profile = new StudentProfile();
        profile.setId(1L);
        profile.setRollNo("24MCS001");
        profile.setUser(user(STUDENT_EMAIL, "Avika Singh"));
        return profile;
    }

    private SupervisorProfile supervisor() {
        SupervisorProfile profile = new SupervisorProfile();
        profile.setId(7L);
        profile.setUser(user(GUIDE_EMAIL, "Dr Test"));
        return profile;
    }

    private Topic topic() {
        Topic topic = new Topic();
        topic.setId(1L);
        topic.setTitle("Edge inference");
        topic.setStudent(student());
        topic.setProposedSupervisor(supervisor());
        return topic;
    }

    private Allocation allocation() {
        Allocation allocation = new Allocation();
        allocation.setId(2L);
        allocation.setStudent(student());
        allocation.setSupervisor(supervisor());
        return allocation;
    }

    private Submission submission() {
        Milestone milestone = new Milestone();
        milestone.setName("Synopsis");

        Submission submission = new Submission();
        submission.setId(5L);
        submission.setMilestone(milestone);
        submission.setAllocation(allocation());
        return submission;
    }
}
