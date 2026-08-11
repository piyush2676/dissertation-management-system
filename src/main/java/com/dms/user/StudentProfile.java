package com.dms.user;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "student_profiles")
@Getter
@Setter
@NoArgsConstructor

public class StudentProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id",nullable = false,unique = true)
    private User user;
    @Column(name = "roll_no",nullable = false,unique = true,length = 32)
    private String rollNo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false,length = 32)
    private Programme programme;
    @Column(length = 32)
    private String department;
    @Column(length = 16)
    private String batch;
    private Integer semester;
}
