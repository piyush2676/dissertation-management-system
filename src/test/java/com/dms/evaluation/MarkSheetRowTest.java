package com.dms.evaluation;

import com.dms.session.DissertationPhase;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MarkSheetRowTest {

    @Test
    void thePassLineIsHalfThePhaseMaximumNotAnAbsoluteForty() {
        // 90 out of 200 would have passed the old absolute-40 convention.
        assertEquals("Fail", row(DissertationPhase.FINAL, 200, "90.00").outcome());
        assertEquals("Pass", row(DissertationPhase.FINAL, 200, "100.00").outcome());
    }

    @Test
    void preAndFinalRowsCompareOnPercentage() {
        MarkSheet.Row pre = row(DissertationPhase.PRE, 100, "70.00");
        MarkSheet.Row fin = row(DissertationPhase.FINAL, 200, "140.00");
        assertEquals(pre.percent(), fin.percent());
        assertEquals(GradeBand.A, pre.band());
        assertEquals(GradeBand.A, fin.band());
    }

    @Test
    void anUnscoredRowIsPendingWithNoBand() {
        MarkSheet.Row row = row(DissertationPhase.FINAL, 200, null);
        assertEquals("Pending", row.outcome());
        assertNull(row.percent());
        assertNull(row.band());
    }

    @Test
    void aRowWithNoRubricInForceCannotBeJudged() {
        // A phase with no seeded scheme has maxTotal 0; dividing by it must not happen.
        MarkSheet.Row row = row(null, 0, "10.00");
        assertNull(row.percent());
        assertEquals("Pending", row.outcome());
    }

    private static MarkSheet.Row row(DissertationPhase phase, int maxTotal, String average) {
        return new MarkSheet.Row(1L, "24MCS001", "Test Student", "Dr Test", "A topic",
                phase, maxTotal, average == null ? 0 : 1,
                average == null ? null : new BigDecimal(average), null, null);
    }
}
