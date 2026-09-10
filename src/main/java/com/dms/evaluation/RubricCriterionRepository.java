package com.dms.evaluation;

import com.dms.session.AcademicSession;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RubricCriterionRepository extends JpaRepository<RubricCriterion, Long> {

    List<RubricCriterion> findBySessionOrderBySequenceNoAsc(AcademicSession session);

    long countBySession(AcademicSession session);
}
