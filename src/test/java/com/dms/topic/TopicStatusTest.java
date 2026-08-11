package com.dms.topic;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.dms.topic.TopicStatus.APPROVED;
import static com.dms.topic.TopicStatus.CHANGES_REQUESTED;
import static com.dms.topic.TopicStatus.DRAFT;
import static com.dms.topic.TopicStatus.PROPOSED;
import static com.dms.topic.TopicStatus.REJECTED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The topic state machine, tested on its own.
 *
 * No Spring context, no database, no mocks — TopicStatus is a plain enum, so
 * the whole class runs in milliseconds. Everything the transition map promises
 * is asserted here, which means TopicService is free to trust canTransitionTo
 * without re-checking anything.
 */
class TopicStatusTest {

    // ---------- each state's legal moves -----------------------------------

    @Test
    void draftGoesOnlyToProposed() {
        assertTrue(DRAFT.canTransitionTo(PROPOSED));

        assertFalse(DRAFT.canTransitionTo(APPROVED));
        assertFalse(DRAFT.canTransitionTo(CHANGES_REQUESTED));
        assertFalse(DRAFT.canTransitionTo(REJECTED));
        assertFalse(DRAFT.canTransitionTo(DRAFT), "a self-transition is not a move");
    }

    @Test
    void proposedGoesToAnyDecision() {
        assertTrue(PROPOSED.canTransitionTo(APPROVED));
        assertTrue(PROPOSED.canTransitionTo(CHANGES_REQUESTED));
        assertTrue(PROPOSED.canTransitionTo(REJECTED));

        assertFalse(PROPOSED.canTransitionTo(DRAFT), "no un-submitting once the guide has it");
    }

    @Test
    void changesRequestedGoesBackToProposedOnly() {
        assertTrue(CHANGES_REQUESTED.canTransitionTo(PROPOSED));

        assertFalse(CHANGES_REQUESTED.canTransitionTo(APPROVED),
                "the guide must see the revision before approving it");
        assertFalse(CHANGES_REQUESTED.canTransitionTo(REJECTED));
        assertFalse(CHANGES_REQUESTED.canTransitionTo(DRAFT));
    }

    // ---------- terminal states --------------------------------------------

    @Test
    void approvedIsTerminal() {
        assertTrue(APPROVED.isTerminal());
        assertTrue(APPROVED.allowedNext().isEmpty());

        for (TopicStatus target : TopicStatus.values()) {
            assertFalse(APPROVED.canTransitionTo(target),
                    "APPROVED must not move to " + target);
        }
    }

    @Test
    void rejectedIsTerminal() {
        assertTrue(REJECTED.isTerminal());
        assertTrue(REJECTED.allowedNext().isEmpty());

        for (TopicStatus target : TopicStatus.values()) {
            assertFalse(REJECTED.canTransitionTo(target),
                    "REJECTED must not move to " + target);
        }
    }

    // ---------- the map itself ---------------------------------------------
    // These are the ones worth having. They fail the day a sixth constant is
    // added without a map entry — which would otherwise surface as a
    // NullPointerException inside canTransitionTo, at runtime, in front of a
    // user.

    @Test
    void everyConstantHasAMapEntry() {
        for (TopicStatus status : TopicStatus.values()) {
            Set<TopicStatus> next = assertDoesNotThrow(status::allowedNext,
                    status + " is missing from the TRANSITIONS map");
            assertNotNull(next);
        }
    }

    @Test
    void allowedNextIsImmutable() {
        Set<TopicStatus> next = PROPOSED.allowedNext();

        assertThrows(UnsupportedOperationException.class, () -> next.add(DRAFT),
                "allowedNext must not hand out a set a caller can mutate");
    }

    @Test
    void canTransitionToAgreesWithAllowedNext() {
        for (TopicStatus from : TopicStatus.values()) {
            for (TopicStatus to : TopicStatus.values()) {
                assertEquals(from.allowedNext().contains(to), from.canTransitionTo(to),
                        from + " -> " + to);
            }
        }
    }

    @Test
    void isTerminalAgreesWithAllowedNext() {
        for (TopicStatus status : TopicStatus.values()) {
            assertEquals(status.allowedNext().isEmpty(), status.isTerminal(), status.name());
        }
    }
}
