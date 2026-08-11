package com.dms.allocation;

import java.util.Map;
import java.util.Set;

public enum AllocationStatus {
    REQUESTED,
    ACCEPTED,
    DECLINED,

    COORDINATOR_ASSIGNED,

    WITHDRAWN;
    private static final Map<AllocationStatus, Set<AllocationStatus>> TRANSITIONS = Map.of(
            REQUESTED,            Set.of(ACCEPTED, DECLINED, WITHDRAWN),
            ACCEPTED,             Set.of(),
            DECLINED,             Set.of(),
            COORDINATOR_ASSIGNED, Set.of(),
            WITHDRAWN,            Set.of()
    );

    public static final Set<AllocationStatus> OCCUPIES_A_SEAT =
            Set.of(ACCEPTED, COORDINATOR_ASSIGNED);

    public static final Set<AllocationStatus> LIVE =
            Set.of(REQUESTED, ACCEPTED, COORDINATOR_ASSIGNED);

    public boolean canTransitionTo(AllocationStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }

    public Set<AllocationStatus> allowedNext() {
        return TRANSITIONS.get(this);
    }

    public boolean occupiesASeat() {
        return OCCUPIES_A_SEAT.contains(this);
    }
}
