package com.dms.attainment;

import com.dms.session.DissertationPhase;
import com.dms.user.Programme;

import java.math.BigDecimal;
import java.util.List;

/**
 * Course outcome attainment, computed from what is already on record: every
 * rubric row carries a CO code since phase 12, and every evaluation stores marks
 * keyed by criterion id since phase 6.
 *
 * <p>Nothing here is stored. An attainment number written into a table goes stale
 * the moment an examiner rescores, and this one is a query.
 */
public record AttainmentReport(
        Programme programme,
        String sessionLabel,
        DissertationPhase phase,
        int cohortSize,
        int scoredCount,
        List<Row> rows) {

    /** A student attains a CO at or above this share of its marks. */
    public static final BigDecimal ATTAINMENT_THRESHOLD_PERCENT = BigDecimal.valueOf(60);

    /** Attainment level 3, 2, 1 by the share of the cohort that cleared the threshold. */
    public static final BigDecimal LEVEL_3_PERCENT = BigDecimal.valueOf(70);
    public static final BigDecimal LEVEL_2_PERCENT = BigDecimal.valueOf(60);
    public static final BigDecimal LEVEL_1_PERCENT = BigDecimal.valueOf(50);

    public record Row(
            String coCode,
            String poMapping,
            List<String> criteria,
            int maxMarks,
            int studentsScored,
            int studentsAttained,
            BigDecimal averagePercent,
            BigDecimal attainmentPercent) {

        /** NBA-style 3/2/1, or 0 when too few of the cohort cleared it. */
        public int level() {
            if (attainmentPercent == null) {
                return 0;
            }
            if (attainmentPercent.compareTo(LEVEL_3_PERCENT) >= 0) {
                return 3;
            }
            if (attainmentPercent.compareTo(LEVEL_2_PERCENT) >= 0) {
                return 2;
            }
            if (attainmentPercent.compareTo(LEVEL_1_PERCENT) >= 0) {
                return 1;
            }
            return 0;
        }

        public boolean measured() {
            return studentsScored > 0;
        }
    }

    public boolean isEmpty() {
        return rows.isEmpty() || scoredCount == 0;
    }
}
