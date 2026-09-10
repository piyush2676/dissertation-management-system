package com.dms.submission;

import java.util.Map;
import java.util.Set;

/**
 * Declared once, validated in the service. An illegal move throws
 * InvalidStateTransitionException and lands on the 409 page rather than
 * quietly corrupting the record.
 */
public enum SubmissionStatus {

    /** Slot exists, nothing uploaded against it yet. */
    DRAFT,

    /** A version is on record and waiting for the guide to pick it up. */
    SUBMITTED,

    /** The guide has started reading it. */
    UNDER_REVIEW,

    /** Accepted. Terminal. */
    APPROVED,

    /** Sent back. The student uploads a new version, which returns it to SUBMITTED. */
    REVISION_REQUESTED,

    /** Refused outright. Terminal. */
    REJECTED;

    private static final Map<SubmissionStatus, Set<SubmissionStatus>> TRANSITIONS = Map.of(
            DRAFT,              Set.of(SUBMITTED),
            SUBMITTED,          Set.of(UNDER_REVIEW),
            UNDER_REVIEW,       Set.of(APPROVED, REVISION_REQUESTED, REJECTED),
            REVISION_REQUESTED, Set.of(SUBMITTED),
            APPROVED,           Set.of(),
            REJECTED,           Set.of()
    );

    /** Statuses a student may upload a new version against. */
    public static final Set<SubmissionStatus> ACCEPTS_UPLOAD =
            Set.of(DRAFT, REVISION_REQUESTED);

    /** Statuses the guide still owes a decision on. */
    public static final Set<SubmissionStatus> AWAITING_GUIDE =
            Set.of(SUBMITTED, UNDER_REVIEW);

    public boolean canTransitionTo(SubmissionStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }

    public Set<SubmissionStatus> allowedNext() {
        return TRANSITIONS.get(this);
    }

    public boolean acceptsUpload() {
        return ACCEPTS_UPLOAD.contains(this);
    }

    public boolean awaitingGuide() {
        return AWAITING_GUIDE.contains(this);
    }
}
