package com.dms.logbook;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.dms.logbook.LogbookEntryStatus.PENDING;
import static com.dms.logbook.LogbookEntryStatus.RETURNED;
import static com.dms.logbook.LogbookEntryStatus.SIGNED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogbookEntryStatusTest {

    @Test
    void aPendingRowCanBeSignedOrReturnedAndNothingElse() {
        assertEquals(Set.of(SIGNED, RETURNED), PENDING.allowedNext());
    }

    @Test
    void aReturnedRowGoesBackToPendingOnly() {
        assertEquals(Set.of(PENDING), RETURNED.allowedNext());
        assertFalse(RETURNED.canTransitionTo(SIGNED), "the guide signs the corrected row, not the returned one");
    }

    @Test
    void signedIsTheOnlyTerminalState() {
        assertTrue(SIGNED.isTerminal(), "signed means sealed");
        assertFalse(PENDING.isTerminal());
        assertFalse(RETURNED.isTerminal());
    }

    @Test
    void theStudentMayEditAnythingUnsigned() {
        assertTrue(PENDING.editableByStudent());
        assertTrue(RETURNED.editableByStudent());
        assertFalse(SIGNED.editableByStudent());
    }
}
