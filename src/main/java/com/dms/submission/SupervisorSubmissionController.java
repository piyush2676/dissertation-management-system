package com.dms.submission;

import com.dms.common.InvalidStateTransitionException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/supervisor/submissions")
@RequiredArgsConstructor
public class SupervisorSubmissionController {

    private final SubmissionService submissionService;

    @ModelAttribute("decisions")
    public List<SubmissionStatus> decisions() {
        return List.of(SubmissionStatus.APPROVED,
                SubmissionStatus.REVISION_REQUESTED,
                SubmissionStatus.REJECTED);
    }

    @GetMapping("")
    public String queue(Authentication authentication, Model model) {
        model.addAttribute("queue", submissionService.queueFor(authentication.getName()));
        return "supervisor/submissions";
    }

    @GetMapping("/{submissionId}")
    public String detail(@PathVariable Long submissionId, Authentication authentication, Model model) {
        model.addAttribute("detail",
                submissionService.detailFor(submissionId, authentication.getName(), false));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new SubmissionDecisionForm());
        }
        return "supervisor/submission";
    }

    /** Picking the work up. Separate from deciding so the student can see it was read. */
    @PostMapping("/{submissionId}/start")
    public String startReview(@PathVariable Long submissionId,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        try {
            submissionService.startReview(authentication.getName(), submissionId);
            redirectAttributes.addFlashAttribute("success", "Marked as under review.");
        } catch (InvalidStateTransitionException ex) {
            redirectAttributes.addFlashAttribute("error", "That submission is not waiting to be picked up.");
        }
        return "redirect:/supervisor/submissions/" + submissionId;
    }

    @PostMapping("/{submissionId}/decide")
    public String decide(@PathVariable Long submissionId,
                         @Valid @ModelAttribute("form") SubmissionDecisionForm form,
                         BindingResult bindingResult,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("form", form);
            redirectAttributes.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + "form", bindingResult);
            return "redirect:/supervisor/submissions/" + submissionId;
        }

        try {
            Submission decided = submissionService.decide(
                    authentication.getName(), submissionId, form.getDecision(), form.getNote());

            redirectAttributes.addFlashAttribute("success", switch (decided.getStatus()) {
                case APPROVED -> "Submission approved.";
                case REVISION_REQUESTED -> "Revision requested. The student can upload a new version.";
                case REJECTED -> "Submission rejected.";
                default -> "Decision recorded.";
            });
        } catch (InvalidStateTransitionException ex) {
            redirectAttributes.addFlashAttribute("error",
                    "Start the review before recording a decision.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/supervisor/submissions/" + submissionId;
    }
}
