package com.dms.change;

import com.dms.allocation.Allocation;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, Long> {

    @EntityGraph(attributePaths = {"preferredSupervisor", "preferredSupervisor.user", "decidedBy"})
    List<ChangeRequest> findByAllocationOrderByRequestedAtDesc(Allocation allocation);

    boolean existsByAllocationAndStatus(Allocation allocation, ChangeRequestStatus status);

    @EntityGraph(attributePaths = {"allocation", "allocation.student", "allocation.student.user",
            "allocation.supervisor", "allocation.supervisor.user", "allocation.topic",
            "preferredSupervisor", "preferredSupervisor.user"})
    List<ChangeRequest> findByStatusOrderByRequestedAtAsc(ChangeRequestStatus status);

    @EntityGraph(attributePaths = {"allocation", "allocation.student", "allocation.student.user",
            "allocation.supervisor", "allocation.supervisor.user", "allocation.topic",
            "preferredSupervisor", "preferredSupervisor.user"})
    Optional<ChangeRequest> findWithGraphById(Long id);

    long countByStatus(ChangeRequestStatus status);
}
