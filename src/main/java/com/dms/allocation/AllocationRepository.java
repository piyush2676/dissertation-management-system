package com.dms.allocation;

import com.dms.session.AcademicSession;
import com.dms.user.StudentProfile;
import com.dms.user.SupervisorProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AllocationRepository extends JpaRepository<Allocation, Long> {
    @EntityGraph(attributePaths = {"supervisor","supervisor.user","topic"})
    Optional<Allocation> findByStudentAndSessionAndStatusIn(StudentProfile student, AcademicSession session, Collection<AllocationStatus> statuses);
    boolean existsByStudentAndSessionAndStatusIn(StudentProfile student, AcademicSession session, Collection<AllocationStatus> statuses);
    long countBySupervisorAndSessionAndStatusIn(SupervisorProfile supervisor, AcademicSession session, Collection<AllocationStatus> statuses);
    @EntityGraph(attributePaths = {"student","student.user","topic"})
    List<Allocation> findBySupervisorAndStatusOrderByRequestedAtAsc(SupervisorProfile supervisor,AllocationStatus status);
    @EntityGraph(attributePaths = {"student","student.user","supervisor","supervisor.user","topic"})
    List<Allocation> findBySessionOrderByRequestedAtDesc(AcademicSession session);
    @EntityGraph(attributePaths = {"student","student.user","supervisor","supervisor.user","session","topic"})
    Optional<Allocation> findWithGraphById(Long id);
    boolean existsByIdAndStudentUserEmail(Long id, String email);
    boolean existsByIdAndSupervisorUserEmail(Long id, String email);
    @EntityGraph(attributePaths = {"supervisor","supervisor.user","topic"})
    List<Allocation> findByStudentOrderByRequestedAtDesc(StudentProfile student);
    @Query("""
           select a.supervisor.id, count(a)
           from Allocation a
           where a.session = :session and a.status in :statuses
           group by a.supervisor.id
           """)
    List<Object[]> countPerSupervisor(@Param("session") AcademicSession session,
                                      @Param("statuses") Collection<AllocationStatus> statuses);
    @EntityGraph(attributePaths = {"student","student.user","topic"})
    List<Allocation> findBySupervisorAndStatusInOrderByDecidedAtDesc(SupervisorProfile supervisor, Collection<AllocationStatus> statuses);
}
