package com.dms.evaluation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GradeBandTest {

    @Test
    void theGuidelineThresholdsAreInclusiveAtTheFloor() {
        assertEquals(GradeBand.S, GradeBand.of(new BigDecimal("81")));
        assertEquals(GradeBand.A, GradeBand.of(new BigDecimal("61")));
        assertEquals(GradeBand.B, GradeBand.of(new BigDecimal("41")));
        assertEquals(GradeBand.C, GradeBand.of(new BigDecimal("40.9")));
    }

    @Test
    void justUnderAFloorFallsToTheBandBelow() {
        assertEquals(GradeBand.A, GradeBand.of(new BigDecimal("80.9")));
        assertEquals(GradeBand.B, GradeBand.of(new BigDecimal("60.9")));
    }

    @Test
    void zeroIsUnsatisfactoryNotAnError() {
        assertEquals(GradeBand.C, GradeBand.of(BigDecimal.ZERO));
    }

    @Test
    void noPercentageMeansNoBand() {
        assertNull(GradeBand.of(null), "an unscored student has no band, not a C");
    }
}
