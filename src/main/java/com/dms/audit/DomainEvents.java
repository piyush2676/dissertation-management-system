package com.dms.audit;

/**
 * The events the workflow publishes. Named rather than generic so a reader can
 * see the whole vocabulary of the system in one place.
 */
public final class DomainEvents {

    private DomainEvents() {
    }

    // ---- topic --------------------------------------------------------------

    public record TopicProposed(String actorEmail, Long entityId, String title) implements DomainEvent {
        public String action() { return "TOPIC_PROPOSED"; }
        public String entityType() { return "Topic"; }
        public String newValue() { return title; }
    }

    public record TopicDecided(String actorEmail, Long entityId, String from, String to)
            implements DomainEvent {
        public String action() { return "TOPIC_" + to; }
        public String entityType() { return "Topic"; }
        public String oldValue() { return from; }
        public String newValue() { return to; }
    }

    // ---- allocation ---------------------------------------------------------

    public record GuideRequested(String actorEmail, Long entityId, String supervisorName)
            implements DomainEvent {
        public String action() { return "GUIDE_REQUESTED"; }
        public String entityType() { return "Allocation"; }
        public String newValue() { return supervisorName; }
    }

    public record GuideDecided(String actorEmail, Long entityId, String from, String to)
            implements DomainEvent {
        public String action() { return "GUIDE_" + to; }
        public String entityType() { return "Allocation"; }
        public String oldValue() { return from; }
        public String newValue() { return to; }
    }

    public record GuideAssigned(String actorEmail, Long entityId, String supervisorName)
            implements DomainEvent {
        public String action() { return "GUIDE_COORDINATOR_ASSIGNED"; }
        public String entityType() { return "Allocation"; }
        public String newValue() { return supervisorName; }
    }

    // ---- submission ---------------------------------------------------------

    public record SubmissionFiled(String actorEmail, Long entityId, String milestone, int versionNo)
            implements DomainEvent {
        public String action() { return "SUBMISSION_FILED"; }
        public String entityType() { return "Submission"; }
        public String newValue() { return milestone + " v" + versionNo; }
    }

    public record SubmissionReviewStarted(String actorEmail, Long entityId, String milestone)
            implements DomainEvent {
        public String action() { return "SUBMISSION_UNDER_REVIEW"; }
        public String entityType() { return "Submission"; }
        public String newValue() { return milestone; }
    }

    public record SubmissionDecided(String actorEmail, Long entityId, String from, String to)
            implements DomainEvent {
        public String action() { return "SUBMISSION_" + to; }
        public String entityType() { return "Submission"; }
        public String oldValue() { return from; }
        public String newValue() { return to; }
    }

    // ---- review -------------------------------------------------------------

    /** entityId is the submission, not the comment: that is what a reader opens. */
    public record ReviewCommented(String actorEmail, Long entityId, String excerpt)
            implements DomainEvent {
        public String action() { return "REVIEW_COMMENTED"; }
        public String entityType() { return "Submission"; }
        public String newValue() { return excerpt; }
    }
}
