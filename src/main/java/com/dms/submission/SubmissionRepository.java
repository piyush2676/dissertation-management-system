package com.dms.submission;

import com.dms.allocation.Allocation;
import com.dms.session.Milestone;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Optional<Submission> findByAllocationAndMilestone(Allocation allocation, Milestone milestone);

    @EntityGraph(attributePaths = "milestone")
    List<Submission> findByAllocationOrderByMilestoneSequenceNoAsc(Allocation allocation);

    @EntityGraph(attributePaths = {"milestone", "allocation", "allocation.student",
            "allocation.student.user", "allocation.topic"})
    List<Submission> findByAllocationSupervisorUserEmailAndStatusInOrderByUpdatedAtAsc(
            String supervisorEmail, Collection<SubmissionStatus> statuses);

    @EntityGraph(attributePaths = {"milestone", "allocation", "allocation.student",
            "allocation.student.user", "allocation.topic"})
    List<Submission> findByAllocationSupervisorUserEmailOrderByUpdatedAtDesc(String supervisorEmail);

    @EntityGraph(attributePaths = {"milestone", "allocation", "allocation.student",
            "allocation.student.user", "allocation.supervisor", "allocation.supervisor.user"})
    Optional<Submission> findWithGraphById(Long id);

    boolean existsByIdAndAllocationStudentUserEmail(Long id, String email);

    boolean existsByIdAndAllocationSupervisorUserEmail(Long id, String email);

    long countByAllocationSupervisorUserEmailAndStatusIn(
            String supervisorEmail, Collection<SubmissionStatus> statuses);

    long countByAllocationStudentUserEmailAndStatusIn(
            String studentEmail, Collection<SubmissionStatus> statuses);

    // ---- search -------------------------------------------------------------

    @EntityGraph(attributePaths = {"milestone", "allocation", "allocation.student",
            "allocation.student.user"})
    @Query("""
           select s from Submission s
           where lower(s.milestone.name) like lower(concat('%', :q, '%'))
             and s.allocation.student.user.email = :email
           order by s.milestone.sequenceNo
           """)
    List<Submission> searchOwnedBy(@Param("q") String q, @Param("email") String email);

    @EntityGraph(attributePaths = {"milestone", "allocation", "allocation.student",
            "allocation.student.user"})
    @Query("""
           select s from Submission s
           where s.allocation.supervisor.user.email = :email
             and (lower(s.milestone.name) like lower(concat('%', :q, '%'))
                  or lower(s.allocation.student.rollNo) like lower(concat('%', :q, '%'))
                  or lower(s.allocation.student.user.fullName) like lower(concat('%', :q, '%')))
           order by s.updatedAt desc
           """)
    List<Submission> searchSupervised(@Param("q") String q, @Param("email") String email);
}
