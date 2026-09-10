package com.dms.viva;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.dms.viva.VivaStatus.CANCELLED;
import static com.dms.viva.VivaStatus.HELD;
import static com.dms.viva.VivaStatus.RESCHEDULED;
import static com.dms.viva.VivaStatus.SCHEDULED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VivaStatusTest {

    @Test
    void aBookingCanBeMovedHeldOrCancelled() {
        assertTrue(SCHEDULED.canTransitionTo(RESCHEDULED));
        assertTrue(SCHEDULED.canTransitionTo(HELD));
        assertTrue(SCHEDULED.canTransitionTo(CANCELLED));
    }

    @Test
    void aMovedBookingCanBeMovedAgain() {
        assertTrue(RESCHEDULED.canTransitionTo(RESCHEDULED),
                "a viva may slip more than once");
    }

    @Test
    void aHeldOrCancelledVivaIsClosed() {
        for (VivaStatus closed : Set.of(HELD, CANCELLED)) {
            assertTrue(closed.isTerminal(), closed + " must be terminal");
            for (VivaStatus target : VivaStatus.values()) {
                assertFalse(closed.canTransitionTo(target), closed + " must not move to " + target);
            }
        }
    }

    @Test
    void pendingIsExactlyTheBookingsStillExpectedToHappen() {
        assertEquals(Set.of(SCHEDULED, RESCHEDULED), VivaStatus.PENDING);
        assertTrue(SCHEDULED.isPending());
        assertTrue(RESCHEDULED.isPending());
        assertFalse(HELD.isPending());
        assertFalse(CANCELLED.isPending());
    }

    @Test
    void everyConstantHasAMapEntry() {
        for (VivaStatus status : VivaStatus.values()) {
            Set<VivaStatus> next = assertDoesNotThrow(status::allowedNext,
                    status + " is missing from the TRANSITIONS map");
            assertNotNull(next);
        }
    }

    @Test
    void canTransitionToAgreesWithAllowedNext() {
        for (VivaStatus from : VivaStatus.values()) {
            for (VivaStatus to : VivaStatus.values()) {
                assertEquals(from.allowedNext().contains(to), from.canTransitionTo(to), from + " -> " + to);
            }
        }
    }

    @Test
    void publishedSetsAreImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> VivaStatus.PENDING.add(HELD));
        assertThrows(UnsupportedOperationException.class, () -> SCHEDULED.allowedNext().add(SCHEDULED));
    }
}
