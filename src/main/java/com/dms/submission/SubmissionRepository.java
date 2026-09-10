package com.dms.submission;

import com.dms.allocation.Allocation;
import com.dms.session.Milestone;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
