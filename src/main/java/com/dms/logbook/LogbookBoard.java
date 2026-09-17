package com.dms.logbook;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * One student's logbook as a page sees it: the register rows plus the header
 * Annexure-4 prints above them. A view record, built inside the transaction, so
 * no lazy association reaches Thymeleaf.
 */
public record LogbookBoard(
        boolean hasAllocation,
        Long allocationId,
        String sessionLabel,
        String rollNo,
        String studentName,
        String thesisCode,
        String topicTitle,
        String supervisorName,
        String coSupervisorName,
        List<Row> rows,
        int nextMeetingNo) {

    /** Guidelines §2.2.2 ask for a weekly update; past this many days the dashboard says so. */
    public static final int CADENCE_DAYS = 7;

    public static LogbookBoard none() {
        return new LogbookBoard(false, null, null, null, null, null, null, null, null, List.of(), 1);
    }

    public record Row(
            Long id,
            int meetingNo,
            Instant meetingAt,
            String workAssigned,
            String workCompleted,
            String challenges,
            LogbookEntryStatus status,
            String supervisorRemarks,
            String signedByName,
            Instant signedAt,
            String entryDigest) {

        public boolean signed() {
            return status == LogbookEntryStatus.SIGNED;
        }

        public boolean editable() {
            return status != null && status.editableByStudent();
        }

        public String shortDigest() {
            return entryDigest == null ? "" : entryDigest.substring(0, 12);
        }
    }

    public long signedCount() {
        return rows.stream().filter(Row::signed).count();
    }

    public long pendingCount() {
        return rows.stream().filter(r -> r.status() == LogbookEntryStatus.PENDING).count();
    }

    public long returnedCount() {
        return rows.stream().filter(r -> r.status() == LogbookEntryStatus.RETURNED).count();
    }

    /** The most recent meeting on record, signed or not. */
    public Instant lastMeetingAt() {
        return rows.stream().map(Row::meetingAt).max(Instant::compareTo).orElse(null);
    }

    public Long daysSinceLastMeeting() {
        Instant last = lastMeetingAt();
        return last == null ? null : Duration.between(last, Instant.now()).toDays();
    }

    /** True once the weekly cadence has lapsed, or when nothing has been recorded at all. */
    public boolean cadenceLapsed() {
        Long days = daysSinceLastMeeting();
        return days == null || days > CADENCE_DAYS;
    }
}
