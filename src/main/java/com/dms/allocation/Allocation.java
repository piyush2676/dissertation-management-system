package com.dms.allocation;

import com.dms.session.AcademicSession;
import com.dms.topic.Topic;
import com.dms.user.StudentProfile;
import com.dms.user.SupervisorProfile;
import com.dms.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Entity
@Table(name = "allocations")
@Getter @Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Allocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id",nullable = false)
    StudentProfile  student;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_id",nullable = false)
    SupervisorProfile supervisor;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    Topic topic;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false,length = 32)
    AllocationStatus status = AllocationStatus.REQUESTED;
    @Column(name = "decision_reason",columnDefinition = "TEXT")
    String decisionReason;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocated_by")
    User allocatedBy;
    @Column(name = "requested_at",nullable = false,updatable = false)
    Instant requestedAt = Instant.now();
    @Column(name = "decided_at")
    Instant decidedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id",nullable = false)
    AcademicSession session;
}
