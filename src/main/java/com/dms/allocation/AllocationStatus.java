package com.dms.allocation;

import java.util.Map;
import java.util.Set;

public enum AllocationStatus {
    REQUESTED,
    ACCEPTED,
    DECLINED,

    COORDINATOR_ASSIGNED,

    WITHDRAWN;
    /**
     * A live placement can be withdrawn since phase 16, because guidelines section
     * 4.11 requires a way to change a supervisor. The state machine only says the
     * move is possible: the one caller allowed to make it is
     * ChangeRequestService.approve, after the coordinator has considered a written
     * request. AllocationService.withdraw still refuses anything but REQUESTED.
     */
    private static final Map<AllocationStatus, Set<AllocationStatus>> TRANSITIONS = Map.of(
            REQUESTED,            Set.of(ACCEPTED, DECLINED, WITHDRAWN),
            ACCEPTED,             Set.of(WITHDRAWN),
            DECLINED,             Set.of(),
            COORDINATOR_ASSIGNED, Set.of(WITHDRAWN),
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
