package com.dms.session;

import com.dms.user.Programme;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "academic_sessions")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AcademicSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(nullable = false,length = 32)
    String label;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false,length = 32)
    Programme programme;
    @Column(name = "start_date", nullable = false)
    LocalDate startDate;
    @Column(name = "end_date",nullable = false)
    LocalDate endDate;
    @Column(nullable = false)
    boolean active;
    @Column(name = "created_at",nullable = false,updatable = false)
    Instant createdAt;
}
