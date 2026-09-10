package com.dms.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiReportRepository extends JpaRepository<AiReport, Long> {

    Optional<AiReport> findByKindAndRefId(ReportKind kind, Long refId);
}
