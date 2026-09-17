package com.dms.logbook;

import com.dms.allocation.Allocation;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LogbookEntryRepository extends JpaRepository<LogbookEntry, Long> {

    @EntityGraph(attributePaths = {"signedBy"})
    List<LogbookEntry> findByAllocationOrderByMeetingNoAsc(Allocation allocation);

    List<LogbookEntry> findByAllocationAndStatusOrderByMeetingNoAsc(Allocation allocation, LogbookEntryStatus status);

    Optional<LogbookEntry> findFirstByAllocationOrderByMeetingNoDesc(Allocation allocation);

    long countByAllocationAndStatus(Allocation allocation, LogbookEntryStatus status);

    @EntityGraph(attributePaths = {"allocation", "allocation.student", "allocation.student.user",
            "allocation.supervisor", "allocation.supervisor.user"})
    Optional<LogbookEntry> findWithGraphById(Long id);

    boolean existsByIdAndAllocationStudentUserEmail(Long id, String email);

    boolean existsByIdAndAllocationSupervisorUserEmail(Long id, String email);

    /** The guide's inbox: every unsigned row across the students they supervise, oldest first. */
    @EntityGraph(attributePaths = {"allocation", "allocation.student", "allocation.student.user"})
    @Query("""
           select e from LogbookEntry e
           where e.allocation.supervisor.user.email = :email
             and e.allocation.status in :live
             and e.status = com.dms.logbook.LogbookEntryStatus.PENDING
           order by e.meetingAt asc
           """)
    List<LogbookEntry> pendingFor(@Param("email") String email,
                                  @Param("live") Collection<com.dms.allocation.AllocationStatus> live);

    @Query("""
           select count(e) from LogbookEntry e
           where e.allocation.supervisor.user.email = :email
             and e.allocation.status in :live
             and e.status = com.dms.logbook.LogbookEntryStatus.PENDING
           """)
    long countPendingFor(@Param("email") String email,
                         @Param("live") Collection<com.dms.allocation.AllocationStatus> live);
}
