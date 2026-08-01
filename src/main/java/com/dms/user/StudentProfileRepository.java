package com.dms.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
    /** The user property is of type User, so the parameter must be too. */
    Optional<StudentProfile> findByUser(User user);

    /** Traverses user -> email. Use this when you only have the login address. */
    Optional<StudentProfile> findByUserEmail(String email);

    Optional<StudentProfile> findByRollNo(String rollNo);
}
