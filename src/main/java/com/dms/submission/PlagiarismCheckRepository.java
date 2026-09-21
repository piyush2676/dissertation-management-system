package com.dms.submission;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlagiarismCheckRepository extends JpaRepository<PlagiarismCheck, Long> {

    @EntityGraph(attributePaths = {"checkedBy"})
    Optional<PlagiarismCheck> findByVersion(SubmissionVersion version);

    @EntityGraph(attributePaths = {"checkedBy", "version"})
    List<PlagiarismCheck> findByVersionIn(Collection<SubmissionVersion> versions);
}
