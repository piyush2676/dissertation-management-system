package com.dms.change;

import java.time.Instant;
import java.util.List;

/** One scholar's change requests as their page sees them. */
public record ChangeRequestBoard(
        boolean hasAllocation,
        Long allocationId,
        String supervisorName,
        String topicTitle,
        String thesisCode,
        boolean pending,
        List<Row> rows) {

    public static ChangeRequestBoard none() {
        return new ChangeRequestBoard(false, null, null, null, null, false, List.of());
    }

    public record Row(
            Long id,
            ChangeKind kind,
            String reason,
            String preferredSupervisorName,
            String proposedTitle,
            ChangeRequestStatus status,
            String decisionNote,
            String decidedByName,
            Instant requestedAt,
            Instant decidedAt) {

        public boolean approved() {
            return status == ChangeRequestStatus.APPROVED;
        }

        public boolean open() {
            return status == ChangeRequestStatus.PENDING;
        }
    }
}
