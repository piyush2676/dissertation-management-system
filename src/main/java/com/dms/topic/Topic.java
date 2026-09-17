package com.dms.topic;

import com.dms.user.StudentProfile;
import com.dms.user.SupervisorProfile;
import com.dms.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "topics")
@Getter
@Setter
@NoArgsConstructor
public class Topic {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id",nullable = false)
    StudentProfile student;
    @Column(nullable = false)
    String title;
    @Column(name = "abstract_text",nullable = false,columnDefinition = "TEXT")
    String abstractText;
    @Column(length = 512)
    String keywords;
    // ---- Annexure-1 / Annexure-2 -------------------------------------------
    @Column(name = "research_domain", length = 128)
    String researchDomain;
    @Column(columnDefinition = "TEXT")
    String objectives;
    @Column(name = "sdg_alignment", length = 255)
    String sdgAlignment;
    @Convert(converter = ExpectedOutcomesConverter.class)
    @Column(name = "expected_outcomes", length = 255)
    Set<ExpectedOutcome> expectedOutcomes = EnumSet.noneOf(ExpectedOutcome.class);
    /** "MT26-001": assigned once, on approval, and printed on every later form. */
    @Column(name = "thesis_code", length = 16)
    String thesisCode;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposed_supervisor_id")
    SupervisorProfile proposedSupervisor;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false,length = 32)
    TopicStatus status = TopicStatus.DRAFT;
    @Column(nullable = false)
    Integer version = 1;
    @Column(name = "decision_reason",columnDefinition = "TEXT")
    String decisionReason;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    User decidedBy;
    @Column(name = "decided_at")
    Instant decidedAt;
    @Column(name = "created_at",nullable = false,updatable = false)
    Instant createdAt = Instant.now();
    @Column(name = "updated_at",nullable = false)
    Instant updatedAt = Instant.now();
    @PreUpdate
    void touch(){
        this.updatedAt = Instant.now();
    }
}
