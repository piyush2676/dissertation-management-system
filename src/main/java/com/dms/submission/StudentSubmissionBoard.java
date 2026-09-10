package com.dms.submission;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The student's milestone register. One row per milestone in the active session,
 * whether or not anything has been submitted against it yet.
 */
public record StudentSubmissionBoard(
        boolean hasAllocation,
        String sessionLabel,
        String supervisorName,
        List<MilestoneRow> rows) {

    /**
     * A milestone plus whatever the student has done about it. Everything after
     * {@code sequenceNo} is null or zero until the first upload.
     */
    public record MilestoneRow(
            Long milestoneId,
            String name,
            String description,
            LocalDate dueDate,
            int weightage,
            int sequenceNo,
            Long submissionId,
            SubmissionStatus status,
            int currentVersionNo,
            boolean late,
            String decisionNote,
            Instant lastSubmittedAt) {

        public boolean started() {
            return submissionId != null;
        }

        /** No submission yet, or the guide has sent it back. */
        public boolean acceptsUpload() {
            return status == null || status.acceptsUpload();
        }

        public boolean overdue() {
            return !started() && LocalDate.now().isAfter(dueDate);
        }

        public boolean awaitingGuide() {
            return status != null && status.awaitingGuide();
        }
    }

    public long submittedCount() {
        return rows.stream().filter(MilestoneRow::started).count();
    }

    public long approvedCount() {
        return rows.stream().filter(r -> r.status() == SubmissionStatus.APPROVED).count();
    }
}
