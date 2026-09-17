package com.dms.evaluation;

import com.dms.session.AcademicSession;
import com.dms.session.DissertationPhase;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RubricCriterionRepository extends JpaRepository<RubricCriterion, Long> {

    List<RubricCriterion> findBySessionOrderBySequenceNoAsc(AcademicSession session);

    List<RubricCriterion> findBySessionAndPhaseOrderBySequenceNoAsc(AcademicSession session, DissertationPhase phase);

    boolean existsBySessionAndPhase(AcademicSession session, DissertationPhase phase);

    long countBySession(AcademicSession session);
}
