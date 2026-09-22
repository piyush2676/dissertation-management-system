package com.dms.panel;

import com.dms.allocation.Allocation;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PanelMemberRepository extends JpaRepository<PanelMember, Long> {

    @EntityGraph(attributePaths = {"member"})
    List<PanelMember> findByAllocationOrderByAddedAtAsc(Allocation allocation);

    @EntityGraph(attributePaths = {"member"})
    List<PanelMember> findByAllocationInOrderByAddedAtAsc(Collection<Allocation> allocations);

    Optional<PanelMember> findByAllocationAndMemberId(Allocation allocation, Long memberId);

    long countByAllocation(Allocation allocation);

    boolean existsByAllocationIdAndMemberEmail(Long allocationId, String email);

    /** Every student this reviewer sits on the panel for, newest allocation first. */
    @EntityGraph(attributePaths = {"allocation", "allocation.student", "allocation.student.user",
            "allocation.supervisor", "allocation.supervisor.user", "allocation.topic"})
    @Query("""
           select p from PanelMember p
           where p.member.email = :email
             and p.allocation.status in :live
           order by p.allocation.id desc
           """)
    List<PanelMember> assignmentsFor(@Param("email") String email,
                                     @Param("live") Collection<com.dms.allocation.AllocationStatus> live);
}
