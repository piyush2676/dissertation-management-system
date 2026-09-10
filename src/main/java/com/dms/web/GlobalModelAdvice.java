package com.dms.web;

import com.dms.notification.NotificationService;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Model attributes the shared layout needs on every page.
 *
 * <p>The navbar bell renders everywhere, so the unread count cannot live in one
 * controller. A ControllerAdvice keeps it out of all of them.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final NotificationService notificationService;

    @ModelAttribute
    public void unreadNotifications(Authentication authentication, Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }
        model.addAttribute("unreadNotifications",
                notificationService.unreadCountFor(authentication.getName()));
    }
}
