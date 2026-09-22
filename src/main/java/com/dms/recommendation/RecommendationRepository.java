package com.dms.recommendation;

import com.dms.allocation.Allocation;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    @EntityGraph(attributePaths = {"submittedBy"})
    Optional<Recommendation> findByAllocation(Allocation allocation);

    @EntityGraph(attributePaths = {"submittedBy"})
    List<Recommendation> findByAllocationIn(Collection<Allocation> allocations);

    boolean existsByAllocation(Allocation allocation);
}
