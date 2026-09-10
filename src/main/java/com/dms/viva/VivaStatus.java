package com.dms.viva;

import java.util.Map;
import java.util.Set;

/** Sixth state machine, same shape as the rest: declared once, checked in the service. */
public enum VivaStatus {

    SCHEDULED,
    RESCHEDULED,
    HELD,
    CANCELLED;

    private static final Map<VivaStatus, Set<VivaStatus>> TRANSITIONS = Map.of(
            SCHEDULED,   Set.of(RESCHEDULED, HELD, CANCELLED),
            RESCHEDULED, Set.of(RESCHEDULED, HELD, CANCELLED),
            HELD,        Set.of(),
            CANCELLED,   Set.of()
    );

    /** Statuses where a date is still expected to be honoured. */
    public static final Set<VivaStatus> PENDING = Set.of(SCHEDULED, RESCHEDULED);

    public boolean canTransitionTo(VivaStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }

    public Set<VivaStatus> allowedNext() {
        return TRANSITIONS.get(this);
    }

    public boolean isPending() {
        return PENDING.contains(this);
    }
}
