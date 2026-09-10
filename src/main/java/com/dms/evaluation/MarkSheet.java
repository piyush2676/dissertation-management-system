package com.dms.evaluation;

import com.dms.user.Programme;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** The department view: one row per allocated student, plus the rubric it was marked against. */
public record MarkSheet(
        Programme programme,
        String sessionLabel,
        List<RubricCriterion> rubric,
        List<Row> rows) {

    public record Row(
            Long allocationId,
            String rollNo,
            String studentName,
            String supervisorName,
            String topicTitle,
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

        /** Institute convention: 40 to pass, and only once someone has marked it. */
        public String outcome() {
            if (average == null) {
                return "Pending";
            }
            return average.compareTo(BigDecimal.valueOf(40)) >= 0 ? "Pass" : "Fail";
        }
    }

    public long scoredCount() {
        return rows.stream().filter(Row::scored).count();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }
}
