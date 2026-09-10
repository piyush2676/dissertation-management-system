package com.dms.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Mounted outside the role prefixes: every signed-in user has notifications, and
 * a row is already scoped to its recipient, so ownership is the only check that
 * matters.
 */
@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("")
    public String list(@RequestParam(defaultValue = "0") int page,
                       Authentication authentication,
                       Model model) {
        model.addAttribute("entries", notificationService.pageFor(authentication.getName(), page));
        return "notifications";
    }

    /** Mark read and follow in one click -- the two always happen together. */
    @PostMapping("/{id}/open")
    public String open(@PathVariable Long id, Authentication authentication) {
        return "redirect:" + notificationService.readAndFollow(authentication.getName(), id)
                .filter(link -> link.startsWith("/") && !link.startsWith("//"))
                .orElse("/notifications");
    }

    @PostMapping("/read-all")
    public String readAll(Authentication authentication, RedirectAttributes redirectAttributes) {
        int marked = notificationService.markAllRead(authentication.getName());
        redirectAttributes.addFlashAttribute("success",
                marked == 0 ? "Nothing unread." : marked + " marked as read.");
        return "redirect:/notifications";
    }
}
