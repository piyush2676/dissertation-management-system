package com.dms.panel;

import com.dms.allocation.Allocation;
import com.dms.user.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * One faculty member on one student's review panel (guidelines section 2.2.1).
 *
 * <p>Membership is what entitles someone who is not the supervising guide to
 * score that student. It carries no marks of its own: a panel member's marks are
 * an ordinary Evaluation row, keyed by examiner like every other.
 */
@Entity
@Table(name = "panel_members")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PanelMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocation_id", nullable = false)
    Allocation allocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    User member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by")
    User addedBy;

    @Column(name = "added_at", nullable = false, updatable = false)
    Instant addedAt = Instant.now();
}
