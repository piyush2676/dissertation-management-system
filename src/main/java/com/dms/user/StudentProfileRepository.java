package com.dms.user;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
    Optional<StudentProfile> findByUser(User user);
    Optional<StudentProfile> findByUserEmail(String email);
    Optional<StudentProfile> findByRollNo(String rollNo);

    @EntityGraph(attributePaths = "user")
    List<StudentProfile> findByProgrammeOrderByRollNoAsc(Programme programme);
}
