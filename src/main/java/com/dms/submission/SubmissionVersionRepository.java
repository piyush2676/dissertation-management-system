package com.dms.submission;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubmissionVersionRepository extends JpaRepository<SubmissionVersion, Long> {

    List<SubmissionVersion> findBySubmissionOrderByVersionNoDesc(Submission submission);

    Optional<SubmissionVersion> findFirstBySubmissionOrderByVersionNoDesc(Submission submission);

    @EntityGraph(attributePaths = {"submission", "submission.allocation",
            "submission.allocation.student", "submission.allocation.student.user",
            "submission.allocation.supervisor", "submission.allocation.supervisor.user"})
    Optional<SubmissionVersion> findWithGraphById(Long id);

    boolean existsBySubmissionAndSha256(Submission submission, String sha256);
}
