package com.dms.outcome;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** One student's outcomes as a page sees them. A view record, built inside the transaction. */
public record OutcomeBoard(
        boolean hasAllocation,
        Long allocationId,
        String rollNo,
        String studentName,
        List<Row> rows) {

    public static OutcomeBoard none() {
        return new OutcomeBoard(false, null, null, null, List.of());
    }

    public record Row(
            Long id,
            OutcomeKind kind,
            String title,
            String venue,
            OutcomeIndexing indexing,
            OutcomeStatus status,
            String reference,
            LocalDate outcomeDate,
            String notes,
            boolean verified,
            String verifiedByName,
            Instant verifiedAt,
            String verificationNote) {

        /** Achieved and verified: the only rows the rules count. */
        public boolean counts() {
            return verified && status.achieved();
        }

        public boolean returned() {
            return !verified && verificationNote != null;
        }
    }

    public long verifiedCount() {
        return rows.stream().filter(Row::verified).count();
    }

    public long awaitingCount() {
        return rows.stream().filter(r -> !r.verified()).count();
    }
}
