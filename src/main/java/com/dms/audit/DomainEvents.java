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

    // ---- logbook ------------------------------------------------------------

    public record LogbookEntryRecorded(String actorEmail, Long entityId, int meetingNo)
            implements DomainEvent {
        public String action() { return "LOGBOOK_RECORDED"; }
        public String entityType() { return "LogbookEntry"; }
        public String newValue() { return "Meeting " + meetingNo; }
    }

    /** to is SIGNED or RETURNED; a signed entry also carries its digest so the trail pins it. */
    public record LogbookEntryDecided(String actorEmail, Long entityId, int meetingNo, String to, String digest)
            implements DomainEvent {
        public String action() { return "LOGBOOK_" + to; }
        public String entityType() { return "LogbookEntry"; }
        public String oldValue() { return "PENDING"; }
        public String newValue() { return "Meeting " + meetingNo + (digest == null ? "" : " " + digest); }
    }

    // ---- outcomes -----------------------------------------------------------

    public record OutcomeReported(String actorEmail, Long entityId, String kind, String title)
            implements DomainEvent {
        public String action() { return "OUTCOME_REPORTED"; }
        public String entityType() { return "Outcome"; }
        public String newValue() { return kind + ": " + title; }
    }

    public record OutcomeVerified(String actorEmail, Long entityId, boolean verified, String summary)
            implements DomainEvent {
        public String action() { return verified ? "OUTCOME_VERIFIED" : "OUTCOME_RETURNED"; }
        public String entityType() { return "Outcome"; }
        public String newValue() { return summary; }
    }

    // ---- plagiarism ---------------------------------------------------------

    /** entityId is the submission, like review comments: that is what a reader opens. */
    public record PlagiarismChecked(String actorEmail, Long entityId, int versionNo,
                                    String similarity, String ai) implements DomainEvent {
        public String action() { return "PLAGIARISM_CHECKED"; }
        public String entityType() { return "Submission"; }
        public String newValue() { return "v" + versionNo + " similarity " + similarity + "% ai " + ai + "%"; }
    }

    // ---- review panel -------------------------------------------------------

    /** entityId is the allocation: a panel is a property of one student's dissertation. */
    public record PanelMemberAdded(String actorEmail, Long entityId, String memberName)
            implements DomainEvent {
        public String action() { return "PANEL_MEMBER_ADDED"; }
        public String entityType() { return "Allocation"; }
        public String newValue() { return memberName; }
    }

    public record PanelMemberRemoved(String actorEmail, Long entityId, String memberName)
            implements DomainEvent {
        public String action() { return "PANEL_MEMBER_REMOVED"; }
        public String entityType() { return "Allocation"; }
        public String oldValue() { return memberName; }
    }

    // ---- Annexure-6 ---------------------------------------------------------

    public record RecommendationFiled(String actorEmail, Long entityId, String verdict, boolean revised)
            implements DomainEvent {
        public String action() { return "RECOMMENDATION_" + (revised ? "REVISED" : "FILED"); }
        public String entityType() { return "Allocation"; }
        public String newValue() { return verdict; }
    }

    // ---- change requests ----------------------------------------------------

    public record ChangeRequested(String actorEmail, Long entityId, String kind) implements DomainEvent {
        public String action() { return "CHANGE_REQUESTED"; }
        public String entityType() { return "ChangeRequest"; }
        public String newValue() { return kind; }
    }

    public record ChangeRequestDecided(String actorEmail, Long entityId, String kind, String to)
            implements DomainEvent {
        public String action() { return "CHANGE_REQUEST_" + to; }
        public String entityType() { return "ChangeRequest"; }
        public String oldValue() { return "PENDING"; }
        public String newValue() { return kind; }
    }

    // ---- title bank ---------------------------------------------------------

    public record TitleBanked(String actorEmail, Long entityId, String title) implements DomainEvent {
        public String action() { return "TITLE_BANKED"; }
        public String entityType() { return "BankedTitle"; }
        public String newValue() { return title; }
    }

    public record TitleWithdrawn(String actorEmail, Long entityId, String title) implements DomainEvent {
        public String action() { return "TITLE_WITHDRAWN"; }
        public String entityType() { return "BankedTitle"; }
        public String oldValue() { return title; }
    }
}
