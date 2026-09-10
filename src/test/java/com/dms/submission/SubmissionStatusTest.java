package com.dms.submission;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.dms.submission.SubmissionStatus.APPROVED;
import static com.dms.submission.SubmissionStatus.DRAFT;
import static com.dms.submission.SubmissionStatus.REJECTED;
import static com.dms.submission.SubmissionStatus.REVISION_REQUESTED;
import static com.dms.submission.SubmissionStatus.SUBMITTED;
import static com.dms.submission.SubmissionStatus.UNDER_REVIEW;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubmissionStatusTest {

    @Test
    void theHappyPathRunsDraftToApproved() {
        assertTrue(DRAFT.canTransitionTo(SUBMITTED));
        assertTrue(SUBMITTED.canTransitionTo(UNDER_REVIEW));
        assertTrue(UNDER_REVIEW.canTransitionTo(APPROVED));
    }

    @Test
    void aRevisionReturnsToSubmitted() {
        assertTrue(UNDER_REVIEW.canTransitionTo(REVISION_REQUESTED));
        assertTrue(REVISION_REQUESTED.canTransitionTo(SUBMITTED));
    }

    @Test
    void aDecisionCannotBeRecordedBeforeTheReviewStarts() {
        assertFalse(SUBMITTED.canTransitionTo(APPROVED));
        assertFalse(SUBMITTED.canTransitionTo(REJECTED));
        assertFalse(SUBMITTED.canTransitionTo(REVISION_REQUESTED));
    }

    @Test
    void aSubmissionCannotSkipStraightToReview() {
        assertFalse(DRAFT.canTransitionTo(UNDER_REVIEW));
        assertFalse(DRAFT.canTransitionTo(APPROVED));
    }

    @Test
    void approvedAndRejectedAreTheOnlyTerminalStates() {
        assertTrue(APPROVED.isTerminal());
        assertTrue(REJECTED.isTerminal());

        for (SubmissionStatus open : Set.of(DRAFT, SUBMITTED, UNDER_REVIEW, REVISION_REQUESTED)) {
            assertFalse(open.isTerminal(), open + " must still have somewhere to go");
        }
    }

    @Test
    void anApprovedSubmissionCannotBeReopened() {
        for (SubmissionStatus target : SubmissionStatus.values()) {
            assertFalse(APPROVED.canTransitionTo(target), "APPROVED must not move to " + target);
            assertFalse(REJECTED.canTransitionTo(target), "REJECTED must not move to " + target);
        }
    }

    @Test
    void onlyAnUnstartedOrReturnedSlotAcceptsAnUpload() {
        assertTrue(DRAFT.acceptsUpload());
        assertTrue(REVISION_REQUESTED.acceptsUpload());

        assertFalse(SUBMITTED.acceptsUpload(), "a student must not replace work already with the guide");
        assertFalse(UNDER_REVIEW.acceptsUpload());
        assertFalse(APPROVED.acceptsUpload());
        assertFalse(REJECTED.acceptsUpload());
    }

    @Test
    void everyStatusThatAcceptsAnUploadCanReachSubmitted() {
        for (SubmissionStatus status : SubmissionStatus.ACCEPTS_UPLOAD) {
            assertTrue(status.canTransitionTo(SUBMITTED),
                    status + " accepts an upload, so it must be able to reach SUBMITTED");
        }
    }

    @Test
    void awaitingGuideIsExactlyTheStatusesOwedADecision() {
        assertEquals(Set.of(SUBMITTED, UNDER_REVIEW), SubmissionStatus.AWAITING_GUIDE);
        assertTrue(SUBMITTED.awaitingGuide());
        assertTrue(UNDER_REVIEW.awaitingGuide());
        assertFalse(REVISION_REQUESTED.awaitingGuide(), "the ball is with the student, not the guide");
    }

    @Test
    void everyConstantHasAMapEntry() {
        for (SubmissionStatus status : SubmissionStatus.values()) {
            Set<SubmissionStatus> next = assertDoesNotThrow(status::allowedNext,
                    status + " is missing from the TRANSITIONS map");
            assertNotNull(next);
        }
    }

    @Test
    void canTransitionToAgreesWithAllowedNext() {
        for (SubmissionStatus from : SubmissionStatus.values()) {
            for (SubmissionStatus to : SubmissionStatus.values()) {
                assertEquals(from.allowedNext().contains(to), from.canTransitionTo(to), from + " -> " + to);
            }
        }
    }

    @Test
    void publishedSetsAreImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> SubmissionStatus.ACCEPTS_UPLOAD.add(APPROVED));
        assertThrows(UnsupportedOperationException.class,
                () -> SubmissionStatus.AWAITING_GUIDE.add(DRAFT));
        assertThrows(UnsupportedOperationException.class,
                () -> DRAFT.allowedNext().add(APPROVED));
    }
}
