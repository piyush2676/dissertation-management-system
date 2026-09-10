package com.dms.review;

import com.dms.submission.SubmissionVersion;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReviewCommentRepository extends JpaRepository<ReviewComment, Long> {

    @EntityGraph(attributePaths = "reviewer")
    List<ReviewComment> findBySubmissionVersionOrderByPageNoAscCreatedAtAsc(SubmissionVersion version);

    @EntityGraph(attributePaths = {"reviewer", "submissionVersion"})
    List<ReviewComment> findBySubmissionVersionInOrderByCreatedAtDesc(Collection<SubmissionVersion> versions);

    long countBySubmissionVersionAndResolvedFalse(SubmissionVersion version);

    @Query("""
           select count(c) from ReviewComment c
           where c.submissionVersion.submission.allocation.student.user.email = :email
             and c.resolved = false
           """)
    long countOpenForStudent(@Param("email") String email);
}
