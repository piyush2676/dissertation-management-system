package com.dms.notification;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
import com.dms.audit.DomainEvents;
import com.dms.submission.Submission;
import com.dms.submission.SubmissionRepository;
import com.dms.topic.Topic;
import com.dms.topic.TopicRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns domain events into notifications for whoever the ball is now with.
 *
 * <p>This class is the whole feature. Nothing in TopicService, AllocationService,
 * SubmissionService or ReviewService changed to add notifications -- they already
 * published the events, and this is a second listener alongside the audit one.
 * That was the claim in the design; this is it being cashed.
 *
 * <p>Events name the actor, never the recipient, so each handler resolves the
 * other party from the record. A notification always goes to the person who now
 * has to do something, never back to the person who just acted.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationListener {

    private final NotificationService notifications;
    private final TopicRepository topicRepository;
    private final AllocationRepository allocationRepository;
    private final SubmissionRepository submissionRepository;

    // ---- topic --------------------------------------------------------------

    @EventListener
    public void on(DomainEvents.TopicProposed event) {
        topicRepository.findWithGraphById(event.entityId()).ifPresent(topic -> {
            if (topic.getProposedSupervisor() == null) {
                return;
            }
            notifications.notify(topic.getProposedSupervisor().getUser(),
                    NotificationType.TOPIC_TO_REVIEW,
                    "New topic to review",
                    studentName(topic) + " proposed: " + topic.getTitle(),
                    "/supervisor/topics");
        });
    }

    @EventListener
    public void on(DomainEvents.TopicDecided event) {
        topicRepository.findWithGraphById(event.entityId()).ifPresent(topic ->
                notifications.notify(topic.getStudent().getUser(),
                        NotificationType.TOPIC_DECIDED,
                        "Your topic was " + readable(event.to()),
                        topic.getTitle(),
                        "/student/topic"));
    }

    // ---- allocation ---------------------------------------------------------

    @EventListener
    public void on(DomainEvents.GuideRequested event) {
        allocationRepository.findWithGraphById(event.entityId()).ifPresent(allocation ->
                notifications.notify(allocation.getSupervisor().getUser(),
                        NotificationType.GUIDE_REQUESTED,
                        "New guide request",
                        studentName(allocation) + " has asked you to supervise them.",
                        "/supervisor/requests"));
    }

    @EventListener
    public void on(DomainEvents.GuideDecided event) {
        allocationRepository.findWithGraphById(event.entityId()).ifPresent(allocation -> {
            boolean studentActed = event.actorEmail()
                    .equals(allocation.getStudent().getUser().getEmail());

            if (studentActed) {
                // A withdrawal. The guide is the one who needs to stop expecting it.
                notifications.notify(allocation.getSupervisor().getUser(),
                        NotificationType.GUIDE_DECIDED,
                        "A guide request was withdrawn",
                        studentName(allocation) + " withdrew their request.",
                        "/supervisor/requests");
            } else {
                notifications.notify(allocation.getStudent().getUser(),
                        NotificationType.GUIDE_DECIDED,
                        "Your guide request was " + readable(event.to()),
                        allocation.getSupervisor().getUser().getFullName()
                                + " recorded a decision on your request.",
                        "/student/guide");
            }
        });
    }

    @EventListener
    public void on(DomainEvents.GuideAssigned event) {
        allocationRepository.findWithGraphById(event.entityId()).ifPresent(allocation -> {
            // A coordinator override concerns both sides, and neither of them acted.
            notifications.notify(allocation.getStudent().getUser(),
                    NotificationType.GUIDE_ASSIGNED,
                    "A guide was assigned to you",
                    "The coordinator placed you with "
                            + allocation.getSupervisor().getUser().getFullName() + ".",
                    "/student/guide");

            notifications.notify(allocation.getSupervisor().getUser(),
                    NotificationType.GUIDE_ASSIGNED,
                    "A student was assigned to you",
                    "The coordinator placed " + studentName(allocation) + " with you.",
                    "/supervisor/requests");
        });
    }

    // ---- submission ---------------------------------------------------------

    @EventListener
    public void on(DomainEvents.SubmissionFiled event) {
        submissionRepository.findWithGraphById(event.entityId()).ifPresent(submission ->
                notifications.notify(submission.getAllocation().getSupervisor().getUser(),
                        NotificationType.SUBMISSION_FILED,
                        "Work filed for review",
                        studentName(submission) + " filed " + event.newValue() + ".",
                        "/supervisor/submissions/" + submission.getId()));
    }

    @EventListener
    public void on(DomainEvents.SubmissionReviewStarted event) {
        submissionRepository.findWithGraphById(event.entityId()).ifPresent(submission ->
                notifications.notify(submission.getAllocation().getStudent().getUser(),
                        NotificationType.SUBMISSION_UNDER_REVIEW,
                        "Your guide is reading your work",
                        submission.getMilestone().getName() + " is under review.",
                        "/student/submissions/" + submission.getId()));
    }

    @EventListener
    public void on(DomainEvents.SubmissionDecided event) {
        submissionRepository.findWithGraphById(event.entityId()).ifPresent(submission ->
                notifications.notify(submission.getAllocation().getStudent().getUser(),
                        NotificationType.SUBMISSION_DECIDED,
                        submission.getMilestone().getName() + " was " + readable(event.to()),
                        submission.getDecisionNote() == null
                                ? "Open the submission to see the record."
                                : submission.getDecisionNote(),
                        "/student/submissions/" + submission.getId()));
    }

    @EventListener
    public void on(DomainEvents.ReviewCommented event) {
        submissionRepository.findWithGraphById(event.entityId()).ifPresent(submission ->
                notifications.notify(submission.getAllocation().getStudent().getUser(),
                        NotificationType.COMMENT_RAISED,
                        "New comment on your work",
                        event.newValue(),
                        "/student/submissions/" + submission.getId()));
    }

    // ---- helpers ------------------------------------------------------------

    private static String studentName(Topic topic) {
        return topic.getStudent().getUser().getFullName();
    }

    private static String studentName(Allocation allocation) {
        return allocation.getStudent().getUser().getFullName();
    }

    private static String studentName(Submission submission) {
        return submission.getAllocation().getStudent().getUser().getFullName();
    }

    /** CHANGES_REQUESTED reads badly in a headline; changes requested does not. */
    private static String readable(String status) {
        return status == null ? "updated" : status.toLowerCase().replace('_', ' ');
    }
}
