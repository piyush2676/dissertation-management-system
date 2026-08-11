package com.dms.session;

import com.dms.user.Programme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface  AcademicSessionRepository extends JpaRepository<AcademicSession, Long> {
    Optional<AcademicSession> findByProgrammeAndActiveTrue(Programme programme);
    List<AcademicSession> findByOrderByStartDateDesc();
    boolean existsByLabelAndProgramme(String label,Programme programme);
    List<AcademicSession> findByProgrammeOrderByStartDateDesc(Programme programme);
}
