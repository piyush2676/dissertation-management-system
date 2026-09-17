package com.dms.evaluation;

import com.dms.session.DissertationPhase;
import com.dms.user.Programme;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The department view: one row per allocated student, plus the rubric each phase
 * was marked against. A cohort mixes phases -- a 3rd-semester and a 4th-semester
 * student share one session -- so the rubric is keyed by phase and every row
 * carries its own maximum.
 */
public record MarkSheet(
        Programme programme,
        String sessionLabel,
        Map<DissertationPhase, List<RubricCriterion>> rubrics,
        List<Row> rows) {

    /** Minimum internal marks for the external viva, guidelines §7.1. */
    public static final BigDecimal PASS_PERCENT = BigDecimal.valueOf(50);

    public record Row(
            Long allocationId,
            String rollNo,
            String studentName,
            String supervisorName,
            String topicTitle,
            DissertationPhase phase,
            int maxTotal,
            int examinerCount,
            BigDecimal average,
            String vivaStatus,
            Instant vivaAt) {

        public boolean scored() {
            return average != null;
        }

        public boolean vivaScheduled() {
            return vivaAt != null;
        }

        /** The average as a share of the phase maximum, so PRE and FINAL rows compare. */
        public BigDecimal percent() {
            if (average == null || maxTotal <= 0) {
                return null;
            }
            return average.multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(maxTotal), 1, RoundingMode.HALF_UP);
        }

        public GradeBand band() {
            return GradeBand.of(percent());
        }

        /** Pass at half the phase maximum, and only once someone has marked it. */
        public String outcome() {
            BigDecimal percent = percent();
            if (percent == null) {
                return "Pending";
            }
            return percent.compareTo(PASS_PERCENT) >= 0 ? "Pass" : "Fail";
        }
    }

    public long scoredCount() {
        return rows.stream().filter(Row::scored).count();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public List<RubricCriterion> rubricFor(DissertationPhase phase) {
        return rubrics.getOrDefault(phase, List.of());
    }

    public int criterionCount() {
        return rubrics.values().stream().mapToInt(List::size).sum();
    }
}
