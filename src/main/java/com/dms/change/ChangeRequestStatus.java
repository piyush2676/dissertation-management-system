package com.dms.change;

import java.util.Map;
import java.util.Set;

/**
 * Declared once and validated in the service, like every other state machine here.
 * Small on purpose: the committee considers a request and answers it, and there is
 * no state in between worth recording.
 */
public enum ChangeRequestStatus {
    /** With the coordinator. The allocation and the topic are untouched meanwhile. */
    PENDING,
    /** Granted. This is the only thing in the system that may undo a live placement. */
    APPROVED,
    /** Refused, with a reason the scholar reads. */
    REJECTED;

    private static final Map<ChangeRequestStatus, Set<ChangeRequestStatus>> TRANSITIONS = Map.of(
            PENDING,  Set.of(APPROVED, REJECTED),
            APPROVED, Set.of(),
            REJECTED, Set.of()
    );

    public boolean canTransitionTo(ChangeRequestStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }

    public Set<ChangeRequestStatus> allowedNext() {
        return TRANSITIONS.get(this);
    }
}
