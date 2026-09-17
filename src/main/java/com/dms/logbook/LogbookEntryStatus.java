package com.dms.logbook;

import java.util.Map;
import java.util.Set;

/**
 * Declared once, validated in the service, like every other state machine here.
 *
 * <p>Two of the three states are the student's: a PENDING or RETURNED row is
 * theirs to edit. SIGNED is terminal on purpose -- that is what "sealed" means,
 * and the certificate relies on it.
 */
public enum LogbookEntryStatus {
    /** Written by the student, waiting for the guide to countersign. */
    PENDING,
    /** Sent back with a remark; the student corrects and re-submits. */
    RETURNED,
    /** Countersigned. Frozen, digested, and listed by the certificate. Terminal. */
    SIGNED;

    private static final Map<LogbookEntryStatus, Set<LogbookEntryStatus>> TRANSITIONS = Map.of(
            PENDING,  Set.of(SIGNED, RETURNED),
            RETURNED, Set.of(PENDING),
            SIGNED,   Set.of()
    );

    /** Statuses the student may still edit. */
    public static final Set<LogbookEntryStatus> EDITABLE_BY_STUDENT = Set.of(PENDING, RETURNED);

    public boolean canTransitionTo(LogbookEntryStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }

    public Set<LogbookEntryStatus> allowedNext() {
        return TRANSITIONS.get(this);
    }

    public boolean editableByStudent() {
        return EDITABLE_BY_STUDENT.contains(this);
    }
}
