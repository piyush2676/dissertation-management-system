package com.dms.review;

import com.dms.common.NotFoundException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Mounted outside /student and /supervisor for the same reason downloads are:
 * both roles act on the same thread, one writing and one resolving, so
 * authorisation is by ownership rather than by URL prefix.
 */
@Controller
@RequestMapping("/review/comments")
@RequiredArgsConstructor
public class ReviewCommentController {

    private final ReviewService reviewService;

    /** Guide posts a remark. */
    @PostMapping("")
    public String comment(@Valid @ModelAttribute("commentForm") ReviewCommentForm form,
                          BindingResult bindingResult,
                          @RequestParam String returnTo,
                          Authentication authentication,
                          RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:" + safe(returnTo);
        }

        try {
            reviewService.comment(authentication.getName(), form.getVersionId(),
                    form.getPageNo(), form.getBody());
            redirectAttributes.addFlashAttribute("success", "Comment posted.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (NotFoundException ex) {
            redirectAttributes.addFlashAttribute("error", "That version is not yours to comment on.");
        }
        return "redirect:" + safe(returnTo);
    }

    /** Student ticks a remark off. */
    @PostMapping("/{commentId}/resolve")
    public String resolve(@PathVariable Long commentId,
                          @RequestParam String returnTo,
                          Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        try {
            reviewService.resolve(authentication.getName(), commentId);
            redirectAttributes.addFlashAttribute("success", "Marked as done.");
        } catch (NotFoundException ex) {
            redirectAttributes.addFlashAttribute("error", "That comment is not yours to resolve.");
        }
        return "redirect:" + safe(returnTo);
    }

    @PostMapping("/{commentId}/reopen")
    public String reopen(@PathVariable Long commentId,
                         @RequestParam String returnTo,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            reviewService.reopen(authentication.getName(), commentId);
            redirectAttributes.addFlashAttribute("success", "Reopened.");
        } catch (NotFoundException ex) {
            redirectAttributes.addFlashAttribute("error", "That comment is not yours to reopen.");
        }
        return "redirect:" + safe(returnTo);
    }

    /**
     * returnTo comes from the page the action was posted from, so it must not be
     * allowed to bounce the user off-site. Only same-application paths pass.
     */
    private static String safe(String returnTo) {
        if (returnTo == null || !returnTo.startsWith("/") || returnTo.startsWith("//")) {
            return "/dashboard";
        }
        return returnTo;
    }
}
