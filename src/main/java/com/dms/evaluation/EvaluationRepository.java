package com.dms.evaluation;

import com.dms.allocation.Allocation;
import com.dms.user.User;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {

    Optional<Evaluation> findByAllocationAndExaminer(Allocation allocation, User examiner);

    @EntityGraph(attributePaths = "examiner")
    List<Evaluation> findByAllocation(Allocation allocation);

    long countByExaminerEmail(String email);
}
