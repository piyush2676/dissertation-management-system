package com.dms.submission;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One submission slot with its full version history, newest first. Rendered to
 * both the student and the supervising guide; the controller decides which
 * actions are offered.
 */
public record SubmissionDetail(
        Long submissionId,
        String studentName,
        String rollNo,
        String supervisorName,
        String topicTitle,
        String milestoneName,
        LocalDate dueDate,
        int weightage,
        SubmissionStatus status,
        boolean late,
        String decisionNote,
        String decidedByName,
        Instant decidedAt,
        List<VersionRow> versions) {

    public record VersionRow(
            Long versionId,
            int versionNo,
            String originalFilename,
            String contentType,
            String sha256,
            long sizeBytes,
            String note,
            Instant submittedAt) {

        /** Short digest for display -- the full 64 characters are noise on screen. */
        public String shortSha() {
            return sha256 == null ? "" : sha256.substring(0, 12);
        }

        public String sizeReadable() {
            if (sizeBytes < 1024) {
                return sizeBytes + " B";
            }
            if (sizeBytes < 1024 * 1024) {
                return Math.round(sizeBytes / 1024.0) + " KB";
            }
            return String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0));
        }
    }

    public boolean hasVersions() {
        return !versions.isEmpty();
    }

    public VersionRow latest() {
        return versions.isEmpty() ? null : versions.get(0);
    }
}
