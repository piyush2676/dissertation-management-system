package com.dms.submission;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The guide's reading desk: what is waiting on them, oldest first, and what they
 * have already decided.
 */
public record SupervisorSubmissionQueue(
        List<QueueRow> pending,
        List<QueueRow> decided) {

    public record QueueRow(
            Long submissionId,
            String studentName,
            String rollNo,
            String topicTitle,
            String milestoneName,
            LocalDate dueDate,
            SubmissionStatus status,
            int currentVersionNo,
            boolean late,
            Instant updatedAt) {

        public boolean resubmission() {
            return currentVersionNo > 1;
        }
    }

    public int pendingCount() {
        return pending.size();
    }
}
