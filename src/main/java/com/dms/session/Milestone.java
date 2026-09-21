package com.dms.session;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "milestones")
@Getter @Setter
@NoArgsConstructor
public class Milestone {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    AcademicSession session;
    /** Which half of the dissertation this review belongs to; the student's semester picks it. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    DissertationPhase phase;
    @Column(nullable = false,length = 128)
    String name;
    /** Which of the required documents this slot collects, if any. Papers are outcomes, not uploads. */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    DeliverableType deliverable;
    @Column(columnDefinition = "TEXT")
    String description;
    @Column(name = "due_date",nullable = false)
    LocalDate dueDate;
    @Column(nullable = false)
    int weightage;
    @Column(name = "sequence_no",nullable = false)
    int sequenceNo;
    @Column(name = "created_at",nullable = false,updatable = false)
    Instant createdAt;
}
