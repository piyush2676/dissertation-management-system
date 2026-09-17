package com.dms.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {
    List<Milestone> findBySessionOrderBySequenceNoAsc(AcademicSession session);
    List<Milestone> findBySessionAndPhaseOrderBySequenceNoAsc(AcademicSession session, DissertationPhase phase);
    Optional<Milestone> findBySessionAndSequenceNo(AcademicSession session, int sequenceNo);
    Optional<Milestone> findFirstBySessionOrderBySequenceNoDesc(AcademicSession session);
    long countBySession(AcademicSession session);
    boolean existsBySessionAndPhase(AcademicSession session, DissertationPhase phase);
    boolean existsBySessionAndSequenceNo(AcademicSession session, int sequenceNo);
}
