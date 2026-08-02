package com.dms.user;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "student_profiles")
@Getter
@Setter
@NoArgsConstructor //@Data also generates equals/hashCode across every field, including user.
// On a JPA entity that means touching a lazy proxy inside hashCode — surprise queries,
// and infinite recursion if the other side ever points back.
public class StudentProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // bigserial ke sath pair karne ke liye
    private Long id;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id",nullable = false,unique = true)
    private User user;
    @Column(name = "roll_no",nullable = false,unique = true,length = 32)
    private String rollNo;
    @Enumerated(EnumType.STRING) //not ordinal
    @Column(nullable = false,length = 32) // widened in V2 for BTECH_MTECH_INTEGRATED
    private Programme programme;
    @Column(length = 32)
    private String department;
    @Column(length = 16)
    private String batch;
    private Integer semester; // ham yaha pe wrapper class isliye use kr rha hain kyuki primitive data type null value hold nhi kr skte
}
