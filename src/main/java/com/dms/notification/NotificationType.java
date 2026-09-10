package com.dms.notification;

/**
 * What happened, from the recipient's point of view rather than the actor's.
 * Drives the icon and grouping on the page.
 */
public enum NotificationType {

    TOPIC_TO_REVIEW,
    TOPIC_DECIDED,

    GUIDE_REQUESTED,
    GUIDE_DECIDED,
    GUIDE_ASSIGNED,

    SUBMISSION_FILED,
    SUBMISSION_UNDER_REVIEW,
    SUBMISSION_DECIDED,

    COMMENT_RAISED
}
