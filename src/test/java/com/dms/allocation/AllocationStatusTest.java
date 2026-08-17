package com.dms.allocation;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.dms.allocation.AllocationStatus.ACCEPTED;
import static com.dms.allocation.AllocationStatus.COORDINATOR_ASSIGNED;
import static com.dms.allocation.AllocationStatus.DECLINED;
import static com.dms.allocation.AllocationStatus.REQUESTED;
import static com.dms.allocation.AllocationStatus.WITHDRAWN;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationStatusTest {

    @Test
    void requestedCanBeAcceptedDeclinedOrWithdrawn() {
        assertTrue(REQUESTED.canTransitionTo(ACCEPTED));
        assertTrue(REQUESTED.canTransitionTo(DECLINED));
        assertTrue(REQUESTED.canTransitionTo(WITHDRAWN));
    }

    @Test
    void requestedCannotJumpToCoordinatorAssigned() {
        assertFalse(REQUESTED.canTransitionTo(COORDINATOR_ASSIGNED));
        assertFalse(REQUESTED.canTransitionTo(REQUESTED));
    }

    @Test
    void everyOutcomeIsTerminal() {
        for (AllocationStatus outcome : Set.of(ACCEPTED, DECLINED, COORDINATOR_ASSIGNED, WITHDRAWN)) {
            assertTrue(outcome.isTerminal(), outcome + " must be terminal");
            assertTrue(outcome.allowedNext().isEmpty(), outcome + " must allow nothing next");

            for (AllocationStatus target : AllocationStatus.values()) {
                assertFalse(outcome.canTransitionTo(target), outcome + " must not move to " + target);
            }
        }
    }

    @Test
    void requestedIsTheOnlyNonTerminalState() {
        assertFalse(REQUESTED.isTerminal());

        long nonTerminal = Set.of(AllocationStatus.values()).stream()
                .filter(s -> !s.isTerminal())
                .count();
        assertEquals(1, nonTerminal, "only REQUESTED may be non-terminal");
    }

    @Test
    void onlyAcceptedAndAssignedTakeASeat() {
        assertTrue(ACCEPTED.occupiesASeat());
        assertTrue(COORDINATOR_ASSIGNED.occupiesASeat());

        assertFalse(REQUESTED.occupiesASeat(), "a pending request must not consume capacity");
        assertFalse(DECLINED.occupiesASeat());
        assertFalse(WITHDRAWN.occupiesASeat());
    }

    @Test
    void occupiesASeatAgreesWithTheSet() {
        for (AllocationStatus status : AllocationStatus.values()) {
            assertEquals(AllocationStatus.OCCUPIES_A_SEAT.contains(status),
                    status.occupiesASeat(), status.name());
        }
    }

    @Test
    void liveIsExactlyTheStatusesInPlay() {
        assertEquals(Set.of(REQUESTED, ACCEPTED, COORDINATOR_ASSIGNED), AllocationStatus.LIVE);

        assertFalse(AllocationStatus.LIVE.contains(DECLINED));
        assertFalse(AllocationStatus.LIVE.contains(WITHDRAWN));
    }

    @Test
    void everySeatTakingStatusIsAlsoLive() {
        assertTrue(AllocationStatus.LIVE.containsAll(AllocationStatus.OCCUPIES_A_SEAT),
                "a status that consumes a seat must count as a live allocation");
    }

    @Test
    void everyConstantHasAMapEntry() {
        for (AllocationStatus status : AllocationStatus.values()) {
            Set<AllocationStatus> next = assertDoesNotThrow(status::allowedNext,
                    status + " is missing from the TRANSITIONS map");
            assertNotNull(next);
        }
    }

    @Test
    void publishedSetsAreImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> AllocationStatus.LIVE.add(DECLINED));
        assertThrows(UnsupportedOperationException.class,
                () -> AllocationStatus.OCCUPIES_A_SEAT.add(REQUESTED));
        assertThrows(UnsupportedOperationException.class,
                () -> REQUESTED.allowedNext().add(COORDINATOR_ASSIGNED));
    }

    @Test
    void canTransitionToAgreesWithAllowedNext() {
        for (AllocationStatus from : AllocationStatus.values()) {
            for (AllocationStatus to : AllocationStatus.values()) {
                assertEquals(from.allowedNext().contains(to), from.canTransitionTo(to),
                        from + " -> " + to);
            }
        }
    }

    @Test
    void isTerminalAgreesWithAllowedNext() {
        for (AllocationStatus status : AllocationStatus.values()) {
            assertEquals(status.allowedNext().isEmpty(), status.isTerminal(), status.name());
        }
    }
}
