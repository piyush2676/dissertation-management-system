package com.dms.provenance;

import java.time.Instant;
import java.util.List;

/**
 * One dissertation's whole life, reconstructed from the audit trail.
 *
 * <p>The design document opens by complaining that nobody can answer "who
 * approved this, and when". This record is that answer.
 */
public record ProvenanceTimeline(
        Long allocationId,
        String rollNo,
        String studentName,
        String programme,
        String sessionLabel,
        String supervisorName,
        String topicTitle,
        List<Entry> entries,
        List<VersionFact> versions,
        String digest,
        String certificateCode) {

    /** One recorded act, taken straight from an audit row. */
    public record Entry(Instant at, String actorEmail, String action, String detail) {

        /** Screaming snake reads badly in a narrative. */
        public String readableAction() {
            return action == null ? "" : action.toLowerCase().replace('_', ' ');
        }
    }

    /** A filed version, with the digest that pins its bytes. */
    public record VersionFact(String milestone, int versionNo, String sha256,
                              long sizeBytes, Instant submittedAt) {
        public String shortSha() {
            return sha256 == null ? "" : sha256.substring(0, 12);
        }
    }

    public boolean hasCertificate() {
        return certificateCode != null;
    }
}
