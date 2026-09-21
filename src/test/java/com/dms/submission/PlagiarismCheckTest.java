package com.dms.submission;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guidelines section 8.3: similarity strictly under 10 percent, AI-generated exactly 0. */
class PlagiarismCheckTest {

    @Test
    void underTenAndZeroPasses() {
        assertTrue(check("9.99", "0.00").passes());
    }

    @Test
    void exactlyTenIsNotUnderTen() {
        assertFalse(check("10.00", "0.00").passes(), "the guidelines say less than 10%");
    }

    @Test
    void anyAiGeneratedContentFails() {
        assertFalse(check("2.00", "0.01").passes(), "the guidelines say strictly 0%");
    }

    private static PlagiarismCheck check(String similarity, String ai) {
        PlagiarismCheck check = new PlagiarismCheck();
        check.setSimilarityPercent(new BigDecimal(similarity));
        check.setAiPercent(new BigDecimal(ai));
        return check;
    }
}
