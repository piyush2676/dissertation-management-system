package com.dms.review;

import com.dms.submission.SubmissionVersion;
import com.dms.user.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * One remark against one uploaded version.
 *
 * <p>Pinned to the version rather than the submission on purpose: feedback stays
 * attached to the file it was written about, so a new version starts with a clean
 * sheet and the old thread stays readable next to the old file.
 */
@Entity
@Table(name = "review_comments")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReviewComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_version_id", nullable = false)
    SubmissionVersion submissionVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    User reviewer;

    /** Optional. Null means the remark is about the document as a whole. */
    @Column(name = "page_no")
    Integer pageNo;

    @Column(nullable = false, columnDefinition = "TEXT")
    String body;

    @Column(nullable = false)
    boolean resolved;

    @Column(name = "resolved_at")
    Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();
}
