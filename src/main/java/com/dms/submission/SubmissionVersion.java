package com.dms.submission;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * One physical upload. Append-only by policy: nothing in the application updates
 * or deletes a version once written, which is what makes the history defensible.
 *
 * <p>sha256 is stored so a file on disk can be proved to be the one that was
 * submitted, and so a re-upload of identical bytes is visible as such.
 */
@Entity
@Table(name = "submission_versions")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubmissionVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    Submission submission;

    @Column(name = "version_no", nullable = false)
    int versionNo;

    /** Relative to the configured upload root. Never rendered to the browser. */
    @Column(name = "storage_path", nullable = false, length = 512)
    String storagePath;

    @Column(name = "original_filename", nullable = false, length = 255)
    String originalFilename;

    @Column(name = "content_type", nullable = false, length = 128)
    String contentType;

    @Column(nullable = false, length = 64)
    String sha256;

    @Column(name = "size_bytes", nullable = false)
    long sizeBytes;

    @Column(columnDefinition = "TEXT")
    String note;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    Instant submittedAt = Instant.now();
}
