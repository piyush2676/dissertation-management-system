package com.dms.outcome;

import com.dms.allocation.Allocation;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OutcomeRepository extends JpaRepository<Outcome, Long> {

    @EntityGraph(attributePaths = {"verifiedBy"})
    List<Outcome> findByAllocationOrderByCreatedAtAsc(Allocation allocation);

    @EntityGraph(attributePaths = {"verifiedBy"})
    List<Outcome> findByAllocationAndVerifiedAtIsNotNullOrderByCreatedAtAsc(Allocation allocation);

    @EntityGraph(attributePaths = {"allocation", "allocation.student", "allocation.student.user"})
    Optional<Outcome> findWithGraphById(Long id);

    boolean existsByIdAndAllocationStudentUserEmail(Long id, String email);

    /** The coordinator's queue: everything not yet verified, across live allocations, oldest first. */
    @EntityGraph(attributePaths = {"allocation", "allocation.student", "allocation.student.user"})
    @Query("""
           select o from Outcome o
           where o.verifiedAt is null
             and o.allocation.status in :live
           order by o.createdAt asc
           """)
    List<Outcome> unverified(@Param("live") Collection<com.dms.allocation.AllocationStatus> live);

    @Query("""
           select count(o) from Outcome o
           where o.verifiedAt is null
             and o.allocation.status in :live
           """)
    long countUnverified(@Param("live") Collection<com.dms.allocation.AllocationStatus> live);
}
