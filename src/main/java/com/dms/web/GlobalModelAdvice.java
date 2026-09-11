package com.dms.web;

import com.dms.notification.NotificationService;
import com.dms.submission.StudentSubmissionBoard;
import com.dms.submission.SubmissionService;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Optional;
import java.util.Set;

/**
 * Model attributes the shared layout needs on every page.
 *
 * <p>The navbar bell and the deadline ticker render everywhere, so neither can
 * live in one controller. A ControllerAdvice keeps them out of all of them.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final NotificationService notificationService;
    private final SubmissionService submissionService;

    @ModelAttribute
    public void layoutAttributes(Authentication authentication, Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }

        String email = authentication.getName();
        model.addAttribute("unreadNotifications", notificationService.unreadCountFor(email));

        // Only students have a next deadline, and only they pay for the lookup.
        Set<String> roles = AuthorityUtils.authorityListToSet(authentication.getAuthorities());
        if (!roles.contains("ROLE_STUDENT")) {
            return;
        }

        try {
            StudentSubmissionBoard board = submissionService.boardFor(email);
            if (!board.hasAllocation()) {
                return;
            }
            model.addAttribute("sessionLabel", board.sessionLabel());

            Optional<StudentSubmissionBoard.MilestoneRow> next = board.rows().stream()
                    .filter(row -> !row.started())
                    .findFirst();

            next.ifPresent(row -> {
                model.addAttribute("nextMilestone", row.name());
                model.addAttribute("nextMilestoneDue", row.dueDate());
                model.addAttribute("nextMilestoneOverdue", row.overdue());
            });
        } catch (RuntimeException ex) {
            // A missing session must not take down every page's header.
            model.addAttribute("nextMilestone", null);
        }
    }
}
