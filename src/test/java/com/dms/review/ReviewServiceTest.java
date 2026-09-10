package com.dms.review;

import com.dms.common.NotFoundException;
import com.dms.submission.Submission;
import com.dms.submission.SubmissionService;
import com.dms.submission.SubmissionVersion;
import com.dms.submission.SubmissionVersionRepository;
import com.dms.user.User;
import com.dms.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final String STUDENT_EMAIL = "student@college.edu";
    private static final String GUIDE_EMAIL = "guide@college.edu";
    private static final String OTHER_EMAIL = "other@college.edu";

    @Mock private ReviewCommentRepository commentRepository;
    @Mock private SubmissionVersionRepository versionRepository;
    @Mock private SubmissionService submissionService;
    @Mock private UserRepository userRepository;

    @InjectMocks private ReviewService service;

    @Test
    void theSupervisingGuideCanComment() {
        SubmissionVersion version = version();

        when(versionRepository.findWithGraphById(9L)).thenReturn(Optional.of(version));
        when(submissionService.isSupervisorOf(5L, GUIDE_EMAIL)).thenReturn(true);
        when(userRepository.findByEmail(GUIDE_EMAIL)).thenReturn(Optional.of(user("Dr Test")));
        when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReviewComment comment = service.comment(GUIDE_EMAIL, 9L, 4, "  Add a baseline.  ");

        assertEquals("Add a baseline.", comment.getBody(), "the body must be stripped");
        assertEquals(4, comment.getPageNo());
        assertFalse(comment.isResolved(), "a new comment starts open");
    }

    @Test
    void anotherGuideCannotCommentOnSomeoneElsesStudent() {
        when(versionRepository.findWithGraphById(9L)).thenReturn(Optional.of(version()));
        when(submissionService.isSupervisorOf(5L, OTHER_EMAIL)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> service.comment(OTHER_EMAIL, 9L, null, "hello"));
        verify(commentRepository, never()).save(any());
    }

    @Test
    void anEmptyCommentIsRefusedBeforeAnythingIsLookedUp() {
        assertThrows(IllegalArgumentException.class, () -> service.comment(GUIDE_EMAIL, 9L, null, "   "));
        verify(versionRepository, never()).findWithGraphById(any());
    }

    @Test
    void pageNumbersStartAtOne() {
        assertThrows(IllegalArgumentException.class, () -> service.comment(GUIDE_EMAIL, 9L, 0, "body"));
    }

    @Test
    void theStudentResolvesAndStampsTheTime() {
        ReviewComment comment = comment(false);

        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(submissionService.isStudentOf(5L, STUDENT_EMAIL)).thenReturn(true);
        when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReviewComment resolved = service.resolve(STUDENT_EMAIL, 1L);

        assertTrue(resolved.isResolved());
        assertTrue(resolved.getResolvedAt() != null, "resolving must be timestamped");
    }

    @Test
    void resolvingTwiceKeepsTheOriginalTimestamp() {
        ReviewComment comment = comment(true);
        java.time.Instant firstTime = comment.getResolvedAt();

        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(submissionService.isStudentOf(5L, STUDENT_EMAIL)).thenReturn(true);
        when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(firstTime, service.resolve(STUDENT_EMAIL, 1L).getResolvedAt());
    }

    @Test
    void reopeningClearsTheResolvedStamp() {
        ReviewComment comment = comment(true);

        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(submissionService.isStudentOf(5L, STUDENT_EMAIL)).thenReturn(true);
        when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReviewComment reopened = service.reopen(STUDENT_EMAIL, 1L);

        assertFalse(reopened.isResolved());
        assertNull(reopened.getResolvedAt());
    }

    @Test
    void theGuideCannotResolveTheirOwnComment() {
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment(false)));
        when(submissionService.isStudentOf(5L, GUIDE_EMAIL)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> service.resolve(GUIDE_EMAIL, 1L));
    }

    @Test
    void aStrangerCannotReadTheThread() {
        when(versionRepository.findWithGraphById(9L)).thenReturn(Optional.of(version()));
        when(submissionService.canRead(5L, OTHER_EMAIL)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> service.commentsOn(9L, OTHER_EMAIL, false));
    }

    // ---- fixtures -----------------------------------------------------------

    private User user(String name) {
        User user = new User();
        user.setId(7L);
        user.setFullName(name);
        return user;
    }

    private SubmissionVersion version() {
        Submission submission = new Submission();
        submission.setId(5L);

        SubmissionVersion version = new SubmissionVersion();
        version.setId(9L);
        version.setVersionNo(1);
        version.setSubmission(submission);
        return version;
    }

    private ReviewComment comment(boolean resolved) {
        ReviewComment comment = new ReviewComment();
        comment.setId(1L);
        comment.setSubmissionVersion(version());
        comment.setReviewer(user("Dr Test"));
        comment.setBody("Add a baseline.");
        comment.setResolved(resolved);
        if (resolved) {
            comment.setResolvedAt(java.time.Instant.now().minusSeconds(3600));
        }
        return comment;
    }
}
