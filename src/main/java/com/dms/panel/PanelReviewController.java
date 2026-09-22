package com.dms.panel;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
import com.dms.common.NotFoundException;
import com.dms.evaluation.EvaluationForm;
import com.dms.evaluation.EvaluationRepository;
import com.dms.evaluation.EvaluationService;

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

/**
 * Panel scoring.
 *
 * <p>Deliberately under {@code /review/**} rather than {@code /supervisor/**}: a
 * panel member may hold only REVIEWER, which the role prefixes do not admit to
 * the supervisor area, and this family of routes already authorises by ownership
 * in the service instead of by URL.
 */
@Controller
@RequestMapping("/review/panel")
@RequiredArgsConstructor
public class PanelReviewController {

    private final PanelService panelService;
    private final EvaluationService evaluationService;
    private final EvaluationRepository evaluationRepository;
    private final AllocationRepository allocationRepository;

    @GetMapping("")
    public String assignments(Authentication authentication, Model model) {
        model.addAttribute("assignments", panelService.assignmentsFor(authentication.getName()));
        return "review/panel";
    }

    @GetMapping("/{allocationId}")
    public String form(@PathVariable Long allocationId, Authentication authentication, Model model) {
        String email = authentication.getName();
        Allocation allocation = loadScorable(allocationId, email);
        model.addAttribute("allocation", allocation);
        model.addAttribute("rubric", evaluationService.rubricFor(allocation));
        if (!model.containsAttribute("form")) {
            EvaluationForm form = new EvaluationForm();
            form.setAllocationId(allocationId);
            // Prefill from this examiner's own row so a correction is an edit.
            evaluationRepository.findByAllocation(allocation).stream()
                    .filter(e -> e.getExaminer().getEmail().equals(email))
                    .findFirst()
                    .ifPresent(existing -> {
                        existing.getScores().forEach((key, value) ->
                                form.getScores().put(Long.valueOf(key), value));
                        form.setRemarks(existing.getRemarks());
                    });
            model.addAttribute("form", form);
        }
        return "review/panel-score";
    }

    @PostMapping("/{allocationId}")
    public String score(@PathVariable Long allocationId,
                        @Valid @ModelAttribute("form") EvaluationForm form,
                        BindingResult bindingResult,
                        Authentication authentication,
                        RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("form", form);
            redirectAttributes.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + "form", bindingResult);
            return "redirect:/review/panel/" + allocationId;
        }
        try {
            evaluationService.score(authentication.getName(), allocationId, form.getScores(), form.getRemarks());
            redirectAttributes.addFlashAttribute("success", "Marks recorded. They join the average on the mark sheet.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/review/panel/" + allocationId;
        }
        return "redirect:/review/panel";
    }

    /** 404 rather than 403: someone who is not on this panel learns nothing about the student. */
    private Allocation loadScorable(Long allocationId, String email) {
        if (!panelService.isPanelMember(allocationId, email)) {
            throw new NotFoundException("Allocation", allocationId);
        }
        return allocationRepository.findWithGraphById(allocationId)
                .orElseThrow(() -> new NotFoundException("Allocation", allocationId));
    }
}
