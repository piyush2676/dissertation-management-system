package com.dms.provenance;

import com.dms.allocation.Allocation;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    @EntityGraph(attributePaths = {"allocation", "allocation.student", "allocation.student.user",
            "allocation.supervisor", "allocation.supervisor.user", "allocation.topic", "issuedBy"})
    Optional<Certificate> findByCode(String code);

    Optional<Certificate> findByAllocation(Allocation allocation);
}
