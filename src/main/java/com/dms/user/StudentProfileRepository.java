package com.dms.user;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
    Optional<StudentProfile> findByUser(User user);
    Optional<StudentProfile> findByUserEmail(String email);
    Optional<StudentProfile> findByRollNo(String rollNo);

    @EntityGraph(attributePaths = "user")
    List<StudentProfile> findByProgrammeOrderByRollNoAsc(Programme programme);

    /** Unscoped: only the coordinator and admin reach this. */
    @EntityGraph(attributePaths = "user")
    @Query("""
           select s from StudentProfile s
           where lower(s.rollNo) like lower(concat('%', :q, '%'))
              or lower(s.user.fullName) like lower(concat('%', :q, '%'))
              or lower(s.user.email) like lower(concat('%', :q, '%'))
           order by s.rollNo
           """)
    List<StudentProfile> search(@Param("q") String q);
}
