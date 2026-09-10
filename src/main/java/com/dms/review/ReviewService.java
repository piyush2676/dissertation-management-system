package com.dms.review;

import com.dms.audit.DomainEvents;
import com.dms.common.NotFoundException;
import com.dms.submission.SubmissionService;
import com.dms.submission.SubmissionVersion;
import com.dms.submission.SubmissionVersionRepository;
import com.dms.user.User;
import com.dms.user.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewService {

    private final ReviewCommentRepository commentRepository;
    private final SubmissionVersionRepository versionRepository;
    private final SubmissionService submissionService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    /**
     * Adds a remark against one version. Only the supervising guide may write; the
     * student reads and resolves.
     */
    public ReviewComment comment(String reviewerEmail, Long versionId, Integer pageNo, String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Write something before posting the comment.");
        }
        if (pageNo != null && pageNo < 1) {
            throw new IllegalArgumentException("Page numbers start at 1.");
        }

        SubmissionVersion version = loadVersion(versionId);

        if (!submissionService.isSupervisorOf(version.getSubmission().getId(), reviewerEmail)) {
            throw new NotFoundException("Submission version", versionId);
        }

        User reviewer = userRepository.findByEmail(reviewerEmail)
                .orElseThrow(() -> new NotFoundException("User " + reviewerEmail + " not found"));

        ReviewComment comment = new ReviewComment();
        comment.setSubmissionVersion(version);
        comment.setReviewer(reviewer);
        comment.setPageNo(pageNo);
        comment.setBody(body.strip());
        comment.setCreatedAt(Instant.now());
        ReviewComment saved = commentRepository.save(comment);

        events.publishEvent(new DomainEvents.ReviewCommented(
                reviewerEmail, version.getSubmission().getId(), saved.getBody()));
        return saved;
    }

    /**
     * The student marks a remark as dealt with. The guide does not resolve their own
     * comments -- the point is a checklist the student works through.
     */
    public ReviewComment resolve(String studentEmail, Long commentId) {
        ReviewComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment", commentId));

        Long submissionId = comment.getSubmissionVersion().getSubmission().getId();
        if (!submissionService.isStudentOf(submissionId, studentEmail)) {
            throw new NotFoundException("Comment", commentId);
        }

        if (!comment.isResolved()) {
            comment.setResolved(true);
            comment.setResolvedAt(Instant.now());
        }
        return commentRepository.save(comment);
    }

    public ReviewComment reopen(String studentEmail, Long commentId) {
        ReviewComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment", commentId));

        Long submissionId = comment.getSubmissionVersion().getSubmission().getId();
        if (!submissionService.isStudentOf(submissionId, studentEmail)) {
            throw new NotFoundException("Comment", commentId);
        }

        comment.setResolved(false);
        comment.setResolvedAt(null);
        return commentRepository.save(comment);
    }

    /** Comments on one version, page order then oldest first. */
    @Transactional(readOnly = true)
    public List<ReviewCommentView> commentsOn(Long versionId, String email, boolean privileged) {
        SubmissionVersion version = loadVersion(versionId);

        if (!privileged && !submissionService.canRead(version.getSubmission().getId(), email)) {
            throw new NotFoundException("Submission version", versionId);
        }

        return commentRepository.findBySubmissionVersionOrderByPageNoAscCreatedAtAsc(version).stream()
                .map(c -> new ReviewCommentView(
                        c.getId(),
                        c.getReviewer().getFullName(),
                        c.getPageNo(),
                        c.getBody(),
                        c.isResolved(),
                        c.getCreatedAt(),
                        c.getResolvedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public long openCountForStudent(String studentEmail) {
        return commentRepository.countOpenForStudent(studentEmail);
    }

    private SubmissionVersion loadVersion(Long versionId) {
        return versionRepository.findWithGraphById(versionId)
                .orElseThrow(() -> new NotFoundException("Submission version", versionId));
    }
}
