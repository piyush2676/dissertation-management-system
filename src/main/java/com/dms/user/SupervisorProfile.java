package com.dms.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "supervisor_profiles")
@Getter
@Setter
@NoArgsConstructor
public class SupervisorProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id",nullable = false,unique = true)
    private User user;
    @Column(length = 128)
    private String designation;
    @Column(length = 128)
    private String department;
    @Column(name = "research_interests",columnDefinition = "TEXT")
    private String researchInterests;
    @Column(name = "max_students",nullable = false)
    private int maxStudents;
}
