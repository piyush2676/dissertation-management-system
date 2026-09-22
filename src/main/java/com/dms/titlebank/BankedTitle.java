package com.dms.titlebank;

import com.dms.topic.ExpectedOutcome;
import com.dms.user.SupervisorProfile;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * A title a guide offers (guidelines section 4.3, Format 3). A scholar may take
 * one or bring their own (4.6), so adopting one only prefills the Annexure-1
 * form: it approves nothing and binds nobody to that guide.
 */
@Entity
@Table(name = "banked_titles")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BankedTitle {

    /** Section 4.3: each faculty member proposes at least this many. */
    public static final int EXPECTED_PER_GUIDE = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_id", nullable = false)
    SupervisorProfile supervisor;

    @Column(nullable = false)
    String title;

    @Column(name = "abstract_text", nullable = false, columnDefinition = "TEXT")
    String abstractText;

    @Column(nullable = false, length = 128)
    String domain;

    /** Reuses Annexure-2's vocabulary rather than inventing a second one. */
    @Enumerated(EnumType.STRING)
    @Column(name = "expected_outcome", nullable = false, length = 32)
    ExpectedOutcome expectedOutcome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    Complexity complexity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    BankedTitleStatus status = BankedTitleStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
