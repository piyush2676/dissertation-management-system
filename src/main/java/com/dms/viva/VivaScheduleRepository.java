package com.dms.viva;

import com.dms.allocation.Allocation;
import com.dms.session.AcademicSession;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VivaScheduleRepository extends JpaRepository<VivaSchedule, Long> {

    Optional<VivaSchedule> findByAllocation(Allocation allocation);

    @EntityGraph(attributePaths = {"allocation", "allocation.student", "allocation.student.user",
            "allocation.supervisor", "allocation.supervisor.user", "allocation.topic"})
    List<VivaSchedule> findByAllocationSessionOrderByScheduledAtAsc(AcademicSession session);

    long countByAllocationSessionAndStatusIn(AcademicSession session, Collection<VivaStatus> statuses);
}
